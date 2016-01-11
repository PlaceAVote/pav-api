(ns com.pav.user.api.test.user.account-settings-test
	(:use midje.sweet)
	(:require [com.pav.user.api.test.utils.utils :refer [flush-redis
																											 flush-dynamo-tables
																											 flush-user-index
																											 test-user
																											 test-fb-user
																											 pav-req]]
						[cheshire.core :as ch]))

(against-background [(before :facts (do
																			(flush-dynamo-tables)
																			(flush-redis)
																			(flush-user-index)))]

	(fact "Retrieve a users account settings"
		(let [{body :body} (pav-req :put "/user" test-user)
					{token :token user_id :user_id} (ch/parse-string body true)
					{status :status body :body} (pav-req :get "/user/me/settings" token {})]
			status => 200
			(ch/parse-string body true) =>
				(merge {:user_id user_id :public true :social_login false}
					(select-keys test-user [:first_name :last_name :dob :gender :email]))))

	(fact "Retrieve a facebook users account settings"
		(let [{body :body} (pav-req :put "/user/facebook" test-fb-user)
					{token :token user_id :user_id} (ch/parse-string body true)
					{status :status body :body} (pav-req :get "/user/me/settings" token {})]
			status => 200
			(ch/parse-string body true) =>
			(merge {:user_id user_id :public true :social_login true}
				(select-keys test-fb-user [:first_name :last_name :dob :gender :email]))))

	(future-fact "Change a public user profile to private"
		(let [{body :body} (pav-req :put "/user" test-user)
					{token :token} (ch/parse-string body true)
					_ (pav-req :post "/user/me/settings?public=false" token {})
					{status :status body :body} (pav-req :get "/user/me/settings" token {})]
			status => 200
			(ch/parse-string body true) => (contains {:public "false"})))

	(fact "Update an existing users residence"
		)

	(fact "Update an existing users Gender"
		)

	(fact "Update an existing users Email"
		)

	(fact "Update an existing users DOB"
		))


