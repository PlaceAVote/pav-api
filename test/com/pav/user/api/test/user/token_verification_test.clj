(ns com.pav.user.api.test.user.token-verification-test
	(:use midje.sweet)
	(:require [cheshire.core :as ch]
						[com.pav.user.api.test.utils.utils :refer [flush-dynamo-tables
																											 flush-redis
																											 flush-user-index
																											 bootstrap-bills
																											 test-user
																											 pav-req]]))

(against-background [(before :facts (do
																			(flush-redis)
																			(flush-dynamo-tables)
																			(flush-user-index)
																			(bootstrap-bills)))]
	(fact "Verify the JWT Token"
		(let [{follower :body} (pav-req :put "/user" test-user)
					{token :token} (ch/parse-string follower true)
					{status :status} (pav-req :get (str "/user/token/validate?token=" token))]
			status => 200))

	(fact "Verify the JWT Token, when invalid, return 401."
		(let [{status :status} (pav-req :get "/user/token/validate?token=rubbish")]
			status => 401)))
