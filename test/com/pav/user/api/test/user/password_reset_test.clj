(ns com.pav.user.api.test.user.password-reset-test
	(:use midje.sweet)
	(:require [com.pav.user.api.test.utils.utils :refer [flush-dynamo-tables
																											 flush-redis
																											 flush-user-index
																											 bootstrap-bills
																											 pav-req]]
						[com.pav.user.api.redis.redis :as redis-dao]))

(def test-user {:email "john@stuff.com" :password "stuff2" :first_name "john" :last_name "stuff" :dob "05/10/1984"
								:country_code "USA" :topics ["Defense"]})

(against-background [(before :facts (do
																			(flush-redis)
																			(flush-dynamo-tables)
																			(flush-user-index)
																			(bootstrap-bills)))]

	(fact "Reset existing user password"
		(let [test-user {:email     "john@placeavote.com" :password "stuff2" :first_name "john"
										 :last_name "stuff" :dob "05/10/1984" :country_code "USA" :topics ["Defense"]}
					_ (pav-req :put "/user" test-user)
					_ (pav-req :post (str "/password/reset?email=" (:email test-user)))
					reset-token (redis-dao/retrieve-password-reset-token-by-useremail (:email test-user))
					_ (pav-req :post "/password/reset/confirm" {:new_password "password1" :reset_token reset-token})
					{status :status} (pav-req :post "/user/authenticate" {:email (:email test-user) :password "password1"})]
			status => 201))

	(fact "Try resetting password with invalid email, should return 401"
		(let [{status :status} (pav-req :post (str "/password/reset?email=rubbish@em.com"))]
			status => 401)))