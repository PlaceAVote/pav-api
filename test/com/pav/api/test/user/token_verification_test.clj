(ns com.pav.api.test.user.token-verification-test
	(:use midje.sweet)
	(:require [com.pav.api.test.utils.utils :refer [flush-dynamo-tables
																									flush-redis
																									flush-es-indexes
																									bootstrap-bills-and-metadata
																									pav-reqv2
                                                  new-pav-user]]))

(against-background [(before :contents (do
																				 (flush-redis)
																				 (flush-dynamo-tables)
                                         (flush-es-indexes)
                                         (bootstrap-bills-and-metadata)))]
	(fact "Verify the JWT Token"
		(let [{follower :body} (pav-reqv2 :put "/user" (new-pav-user))
					{token :token} follower
					{status :status} (pav-reqv2 :get (str "/user/token/validate?token=" token))]
			status => 200))

	(fact "Verify the JWT Token, when invalid, return 401."
		(let [{status :status} (pav-reqv2 :get "/user/token/validate?token=rubbish")]
			status => 401)))
