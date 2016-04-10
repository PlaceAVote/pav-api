(ns com.pav.api.test.user.token-verification-test
	(:use midje.sweet)
	(:require [cheshire.core :as ch]
						[com.pav.api.test.utils.utils :refer [flush-dynamo-tables
																											 flush-redis
																											 flush-es-indexes
																											 bootstrap-bills-and-metadata
																											 test-user
																											 pav-req]]))

(against-background [(before :facts (do
																			(flush-redis)
																			(flush-dynamo-tables)
																			(flush-es-indexes)
																			(bootstrap-bills-and-metadata)))]
	(fact "Verify the JWT Token"
		(let [{follower :body} (pav-req :put "/user" test-user)
					{token :token} (ch/parse-string follower true)
					{status :status} (pav-req :get (str "/user/token/validate?token=" token))]
			status => 200))

	(fact "Verify the JWT Token, when invalid, return 401."
		(let [{status :status} (pav-req :get "/user/token/validate?token=rubbish")]
			status => 401)))
