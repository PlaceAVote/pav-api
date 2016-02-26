(ns com.pav.user.api.test.user.user-profile-test
	(:use midje.sweet)
	(:require [com.pav.user.api.test.utils.utils :refer [flush-dynamo-tables
																											 flush-redis
																											 flush-es-indexes
																											 bootstrap-bills
																											 test-user
																											 test-fb-user
																											 pav-req]]
						[cheshire.core :as ch]))


(def searchable-profile {:email "peter@pl.com" :password "stuff2" :first_name "peter" :last_name "pan" :dob "05/10/1984"
												 :country_code "USA" :topics ["Defense"] :gender "male"})

(against-background [(before :facts (do
																			(flush-dynamo-tables)
																			(flush-redis)
																			(flush-es-indexes)
																			(bootstrap-bills)))]
	(fact "Retrieve a users profile in relation to current user"
		(let [{caller :body} (pav-req :put "/user" test-user)
					{token :token} (ch/parse-string caller true)
					{search-user :body} (pav-req :put "/user" searchable-profile)
					{user_id :user_id} (ch/parse-string search-user true)
					_ (pav-req :put (str "/user/follow") token {:user_id user_id})
					{status :status body :body} (update-in (pav-req :get (str "/user/" user_id "/profile") token {}) [:body]
																				#(ch/parse-string % true))]
			status => 200
			(keys body) => [:user_id :first_name :last_name :country_code :public :total_followers :total_following :following]
			body => (contains {:total_followers 1 :total_following 0 :following true} :in-any-order)))

	(fact "Retrieve a users profile without an authentication token, Then verify user is returned."
		(let [{caller :body} (pav-req :put "/user" test-user)
					{token :token} (ch/parse-string caller true)
					{search-user :body} (pav-req :put "/user" searchable-profile)
					{user_id :user_id} (ch/parse-string search-user true)
					_ (pav-req :put (str "/user/follow") token {:user_id user_id})
					{status :status body :body} (update-in (pav-req :get (str "/user/" user_id "/profile")) [:body]
																				#(ch/parse-string % true))]
			status => 200
			(keys body) => [:user_id :first_name :last_name :country_code :public :total_followers :total_following :following]
			body => (contains {:total_followers 1 :total_following 0 :following false} :in-any-order)))

  ;;FUTURE TEST CASE ONCE THE PRIVATE PROFILE FUNCTIONALITY IS ENABLED ON THE FRONTEND.
	(future-fact "Retrieve a user profile, when there profile is private, Then return 401 error"
		(let [{caller :body} (pav-req :put "/user" test-user)
					{token :token} (ch/parse-string caller true)
					{search-user :body} (pav-req :put "/user" searchable-profile)
					{user_id :user_id private-user-token :token} (ch/parse-string search-user true)
					;;update the users profile via settings endpoint.  We don't currently support this option on signup.
					_ (pav-req :post "/user/me/settings" private-user-token {:public false})
					{status :status } (pav-req :get (str "/user/" user_id "/profile") token {})]
			status => 401))

  (fact "Retrieve a user profile, when the user profile doesn't exist, Then return 401 error"
    (let [{caller :body} (pav-req :put "/user" test-user)
          {token :token} (ch/parse-string caller true)
          {status :status } (pav-req :get (str "/user/21312321312/profile") token {})]
      status => 404))

	(fact "Retrieve the current users profile"
		(let [{caller :body} (pav-req :put "/user" test-user)
					{token :token} (ch/parse-string caller true)
					{status :status body :body} (update-in (pav-req :get "/user/me/profile" token {}) [:body] #(ch/parse-string % true))]
			status => 200
			(keys body) => (contains [:user_id :first_name :last_name :country_code :public :total_followers :total_following] :in-any-order)
			body => (contains {:total_followers 0 :total_following 0} :in-any-order)))

	(fact "Retrieve the current users profile when the users origin is facebook"
		(let [{caller :body} (pav-req :put "/user/facebook" test-fb-user)
					{token :token} (ch/parse-string caller true)
					{status :status body :body} (update-in (pav-req :get "/user/me/profile" token {}) [:body] #(ch/parse-string % true))]
			status => 200
			(keys body) => (contains [:user_id :first_name :last_name :country_code :public :total_followers :img_url :total_following] :in-any-order)
			body => (contains {:total_followers 0 :total_following 0} :in-any-order)))

	(fact "Follow/following another user"
		(let [{follower :body} (pav-req :put "/user" test-user)
					{my_id :user_id token :token} (ch/parse-string follower true)
					{being-followed :body} (pav-req :put "/user" searchable-profile)
					{pauls_user_id :user_id} (ch/parse-string being-followed true)
					{create_status :status} (pav-req :put (str "/user/follow") token {:user_id pauls_user_id})
					{my-following :body} (pav-req :get (str "/user/me/following") token {})
					{paul-following :body} (pav-req :get (str "/user/" pauls_user_id "/following") token {})
					{my-followers :body} (pav-req :get (str "/user/me/followers") token {})
					{pauls-followers :body} (pav-req :get (str "/user/" pauls_user_id "/followers") token {})]
			create_status => 201
			(ch/parse-string my-following true) => (contains {:user_id pauls_user_id :first_name "peter" :last_name "pan" :img_url nil
																												:follower_count 1})
			(ch/parse-string paul-following true) => []
			(ch/parse-string my-followers true) => []
			(ch/parse-string pauls-followers true) => (contains {:user_id my_id :first_name "john" :last_name "stuff" :img_url nil
																													 :follower_count 0})))

	(fact "Follow a user, When that user is the same person, Then return 400 error."
    (let [{body :body} (pav-req :put "/user" test-user)
          {user_id :user_id token :token} (ch/parse-string body true)
          {status :status} (pav-req :put (str "/user/follow") token {:user_id user_id})]
      status => 400))

	(fact "Unfollow user"
		(let [{follower :body} (pav-req :put "/user" test-user)
					{token :token} (ch/parse-string follower true)
					{being-followed :body} (pav-req :put "/user" searchable-profile)
					{pauls_user_id :user_id} (ch/parse-string being-followed true)
					_ (pav-req :put (str "/user/follow") token {:user_id pauls_user_id})
					{unfollow-status :status} (pav-req :delete (str "/user/unfollow") token {:user_id pauls_user_id})
					{my-following :body} (pav-req :get (str "/user/me/following") token {})
					{pauls-followers :body} (pav-req :get (str "/user/" pauls_user_id "/followers") token {})]
			unfollow-status => 204
			(ch/parse-string my-following true) => []
			(ch/parse-string pauls-followers true) => [])))
