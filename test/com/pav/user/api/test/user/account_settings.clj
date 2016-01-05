(ns com.pav.user.api.test.user.account-settings
	(:use midje.sweet)
	(:require [com.pav.user.api.test.utils.utils :refer [flush-redis
																											 flush-dynamo-tables
																											 flush-user-index
																											 pav-req]]))

(def test-user {:email "john@stuff.com" :password "stuff2" :first_name "john" :last_name "stuff" :dob "05/10/1984"
								:country_code "USA" :topics ["Defense"]})

(against-background [(before :facts (do
																			(flush-dynamo-tables)
																			(flush-redis)
																			(flush-user-index)))]

	(fact "Change a public user profile to private"
		)

	(fact "Update an existing users residence"
		)

	(fact "Update an existing users Gender"
		)

	(fact "Update an existing users Email"
		)

	(fact "Update an existing users DOB"
		))


