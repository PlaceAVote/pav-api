(ns com.pav.user.api.test.user.user-profile-test
	(:use midje.sweet)
	(:require [com.pav.user.api.test.utils.utils :refer [flush-dynamo-tables
																											 flush-redis
																											 flush-user-index
																											 bootstrap-bills
																											 pav-req]]
						[cheshire.core :as ch]))

(def test-user {:email "john@stuff.com" :password "stuff2" :first_name "john" :last_name "stuff" :dob "05/10/1984"
								:country_code "USA" :topics ["Defense"]})

(def searchable-profile {:email "peter@pl.com" :password "stuff2" :first_name "peter" :last_name "pan" :dob "05/10/1984"
												 :country_code "USA" :topics ["Defense"]})

(against-background [(before :facts (do
																			(flush-dynamo-tables)
																			(flush-redis)
																			(flush-user-index)
																			(bootstrap-bills)))]
	(future-fact "Retrieve a users profile in relation to current user"
		(let [{caller :body} (pav-req :put "/user" test-user)
					{token :token} (ch/parse-string caller true)
					{search-user :body} (pav-req :put "/user" searchable-profile)
					{user_id :user_id} (ch/parse-string search-user true)
					_ (pav-req :put (str "/user/follow") token {:user_id user_id})
					{status :status body :body} (pav-req :get (str "/user/" user_id "/profile") token {})]
			status => 200
			(ch/parse-string body true) => (merge (dissoc (ch/parse-string search-user true) :email :topics :token)
																			 {:following true
																				:total_followers 1
																				:total_following 0})))

	(future-fact "Retrieve the current users profile"
		(let [{caller :body} (pav-req :put "/user" test-user)
					{token :token} (ch/parse-string caller true)
					{status :status body :body} (pav-req :get "/user/me/profile" token {})]
			status => 200
			(ch/parse-string body true) => (merge (dissoc (ch/parse-string caller true) :email :topics :token)
																			 {:total_followers 0
																				:total_following 0})))

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
