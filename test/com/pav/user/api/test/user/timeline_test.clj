(ns com.pav.user.api.test.user.timeline-test
	(:use midje.sweet)
	(:require [cheshire.core :as ch]
						[com.pav.user.api.test.utils.utils :refer [flush-dynamo-tables
																											 flush-redis
																											 persist-timeline-event
																											 flush-user-index
																											 bootstrap-bills
																											 pav-req]]))

(def test-user {:email "john@stuff.com" :password "stuff2" :first_name "john" :last_name "stuff" :dob "05/10/1984"
								:country_code "USA" :topics ["Defense"]})

(against-background [(before :facts (do
																			(flush-redis)
																			(flush-dynamo-tables)
																			(flush-user-index)
																			(bootstrap-bills)))]
	(fact "Try Retrieving users profile timeline with invalid token"
		(let [{status :status} (pav-req :get "/user/me/timeline" "rubbish token" {})]
			status => 401))

	(fact "Retrieve current users activity timeline"
		(let [{body :body} (pav-req :put "/user" test-user)
					{token :token user_id :user_id} (ch/parse-string body true)
					timeline-events [{:type       "comment" :bill_id "s1182-114" :user_id user_id :timestamp 1446479124991 :comment_id "comment:1"
														:bill_title "A bill to exempt application of JSA attribution rule in case of existing agreements."
														:score      0 :body "Comment text goes here!!"}
													 {:type       "vote" :bill_id "s1182-114" :user_id user_id
														:bill_title "A bill to exempt application of JSA attribution rule in case of existing agreements."
														:timestamp  1446462364297}]
					_ (persist-timeline-event timeline-events)
					{status :status body :body} (pav-req :get "/user/me/timeline" token {})
					{next-page :next-page results :results} (ch/parse-string body true)]
			status => 200
			next-page => 0
			results => (contains timeline-events)))

	(fact "Retrieve a users activity timeline"
		(let [{body :body} (pav-req :put "/user" test-user)
					{token :token} (ch/parse-string body true)
					timeline-events [{:type       "comment" :bill_id "s1182-114" :user_id "user102" :timestamp 1446479124991 :comment_id "comment:1"
														:bill_title "A bill to exempt application of JSA attribution rule in case of existing agreements."
														:score      0 :body "Comment text goes here!!"}
													 {:type       "vote" :bill_id "s1182-114" :user_id "user102"
														:bill_title "A bill to exempt application of JSA attribution rule in case of existing agreements."
														:timestamp  1446462364297}]
					_ (persist-timeline-event timeline-events)
					{status :status body :body} (pav-req :get "/user/user102/timeline" token {})
					{next-page :next-page results :results} (ch/parse-string body true)]
			status => 200
			next-page => 0
			results => (contains timeline-events))))

