(ns com.pav.user.api.test.user.password-reset-test
	(:use midje.sweet)
	(:require [com.pav.user.api.test.utils.utils :refer [flush-dynamo-tables
																											 flush-redis
																											 flush-user-index
																											 bootstrap-bills
																											 test-user
																											 pav-req]]
						[com.pav.user.api.redis.redis :as redis-dao]
						[cheshire.core :as ch]))

(against-background [(before :facts (do
																			(flush-redis)
																			(flush-dynamo-tables)
																			(flush-user-index)
																			(bootstrap-bills)))]

	(fact "Reset existing user password"
		(let [_ (pav-req :put "/user" test-user)
					_ (pav-req :post (str "/password/reset?email=" (:email test-user)))
					reset-token (redis-dao/retrieve-password-reset-token-by-useremail (:email test-user))
					_ (pav-req :post "/password/reset/confirm" {:new_password "password1" :reset_token reset-token})
					{status :status} (pav-req :post "/user/authenticate" {:email (:email test-user) :password "password1"})]
			status => 201))

	(fact "Try resetting password with invalid email, should return 401"
		(let [{status :status} (pav-req :post (str "/password/reset?email=rubbish@em.com"))]
			status => 401))

	(fact "Given the users current password and a new password, Then change the users password and confirm authentication"
		(let [{body :body} (pav-req :put "/user" test-user)
					{token :token} (ch/parse-string body true)
					_ (pav-req :post "/password/change" token {:current_password (:password test-user) :new_password "password1"})
					{status :status} (pav-req :post "/user/authenticate" {:email (:email test-user) :password "password1"})]
			status => 201))

	(fact "Given the users current password and a new password, When current password is invalid, Then return 401 response"
		(let [{body :body} (pav-req :put "/user" test-user)
					{token :token} (ch/parse-string body true)
					{status :status} (pav-req :post "/password/change" token {:current_password "invalid-password" :new_password "password1"})]
			status => 401)))