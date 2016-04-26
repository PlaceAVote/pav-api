(ns com.pav.api.test.user.user-profile-test
	(:use midje.sweet)
	(:require [com.pav.api.test.utils.utils :refer [flush-dynamo-tables
                                                  flush-redis
                                                  flush-es-indexes
                                                  bootstrap-bills-and-metadata
                                                  pav-req
                                                  new-pav-user
                                                  new-fb-user]]))


(against-background [(before :facts (do
                                      (flush-dynamo-tables)
                                      (flush-redis)
                                      (flush-es-indexes)
                                      (bootstrap-bills-and-metadata)))]

  (facts "Test cases covering retrieval of user profiles and following and unfollowing users"

    (fact "Retrieve a users profile in relation to current user"
      (let [{caller-token :token} (:body (pav-req :put "/user" (new-pav-user)))
            {following :user_id} (:body (pav-req :put "/user" (new-pav-user)))
            _ (pav-req :put (str "/user/follow") caller-token {:user_id following})
            {status :status body :body} (pav-req :get (str "/user/" following "/profile") caller-token {})]
        status => 200
        (set (keys body)) => (set [:user_id :first_name :last_name :country_code :state :public
                                   :total_followers :total_following :following])
        body => (contains {:total_followers 1 :total_following 0 :following true} :in-any-order)))

    (fact "Retrieve a users profile without an authentication token, Then verify user is returned."
      (let [{caller-token :token} (:body (pav-req :put "/user" (new-pav-user)))
            {following :user_id} (:body (pav-req :put "/user" (new-pav-user)))
            _ (pav-req :put (str "/user/follow") caller-token {:user_id following})
            {status :status body :body} (pav-req :get (str "/user/" following "/profile"))]
        status => 200
        (set (keys body)) => (set [:user_id :first_name :last_name :country_code :state :public
                                   :total_followers :total_following :following])
        body => (contains {:total_followers 1 :total_following 0 :following false} :in-any-order)))

    (fact "Retrieve a user profile, when the user profile doesn't exist, Then return 404 error"
      (let [{caller :body} (pav-req :put "/user" (new-pav-user))
            {status :status response :body} (pav-req :get (str "/user/21312321312/profile") (:token caller) {})]
        status => 404
        response => {:error {:error_message "User Profile does not exist"}}))

    (fact "Retrieve the current users profile"
      (let [{user :body} (pav-req :put "/user" (new-pav-user))
            {status :status body :body} (pav-req :get "/user/me/profile" (:token user) {})]
        status => 200
        (keys body) => (contains [:user_id :first_name :last_name :country_code :state :public
                                  :total_followers :total_following
                                  :email :zipcode :lat :lng :gender :created_at :district] :in-any-order)
        body => (contains {:total_followers 0 :total_following 0} :in-any-order)))

    (fact "Retrieve the current users profile when the users origin is facebook"
      (let [{caller :body} (pav-req :put "/user/facebook" (new-fb-user))
            {status :status body :body} (pav-req :get "/user/me/profile" (:token caller) {})]
        status => 200
        (keys body) => (contains [:user_id :first_name :last_name :country_code :state :public
                                  :total_followers :img_url :total_following
                                  :email :zipcode :lat :lng :gender :created_at :district] :in-any-order)
        body => (contains {:total_followers 0 :total_following 0} :in-any-order)))

    (fact "Follow/following another user"
      (let [{follower :body} (pav-req :put "/user" (new-pav-user {:first_name "john" :last_name "stuff"}))
            {my_id :user_id token :token} follower
            {being-followed :body} (pav-req :put "/user" (new-pav-user {:first_name "paul" :last_name "the blow"}))
            {pauls_user_id :user_id} being-followed
            {create_status :status} (pav-req :put (str "/user/follow") token {:user_id pauls_user_id})
            {my-following :body} (pav-req :get (str "/user/me/following") token {})
            {paul-following :body} (pav-req :get (str "/user/" pauls_user_id "/following") token {})
            {my-followers :body} (pav-req :get (str "/user/me/followers") token {})
            {pauls-followers :body} (pav-req :get (str "/user/" pauls_user_id "/followers") token {})]
        create_status => 201
        my-following => (contains {:user_id pauls_user_id :first_name "paul" :last_name "the blow" :img_url nil :follower_count 1})
        paul-following => []
        my-followers => []
        pauls-followers => (contains {:user_id my_id :first_name "john" :last_name "stuff" :img_url nil :follower_count 0})))

    (fact "Follow a user, When that user is the same person, Then return 400 error."
      (let [{body :body} (pav-req :put "/user" (new-pav-user))
            {user_id :user_id token :token} body
            {status :status} (pav-req :put (str "/user/follow") token {:user_id user_id})]
        status => 400))

    (fact "Unfollow user"
      (let [{follower :body} (pav-req :put "/user" (new-pav-user))
            {token :token} follower
            {being-followed :body} (pav-req :put "/user" (new-pav-user))
            {pauls_user_id :user_id} being-followed
            _ (pav-req :put (str "/user/follow") token {:user_id pauls_user_id})
            {unfollow-status :status} (pav-req :delete (str "/user/unfollow") token {:user_id pauls_user_id})
            {my-following :body} (pav-req :get (str "/user/me/following") token {})
            {pauls-followers :body} (pav-req :get (str "/user/" pauls_user_id "/followers") token {})]
        unfollow-status => 204
        my-following => []
        pauls-followers => []))

    (fact "Given a user with no followers, When the user follows another user, Then publish event to users timeline."
      (let [{follower :body} (pav-req :put "/user" (new-pav-user))
            {follower-token :token} follower
            {being-followed :body} (pav-req :put "/user" (new-pav-user))
            {followed_user_id :user_id} being-followed
            _ (pav-req :put (str "/user/follow") follower-token {:user_id followed_user_id})
            _ (Thread/sleep 2000)
            {status :status body :body} (pav-req :get "/user/me/timeline" follower-token {})
            {results :results} body]
        status => 200
        (keys (first results)) => (just [:event_id :user_id :following_id :timestamp :type
                                         :last_name :first_name] :in-any-order)))))
