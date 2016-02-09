(ns com.pav.user.api.test.user.notification-test
	(:use midje.sweet)
	(:require [com.pav.user.api.test.utils.utils :refer [pav-req
																											 persist-notification-event
																											 flush-dynamo-tables
																											 flush-redis
																											 flush-es-indexes
																											 test-user
																											 bootstrap-bills]]
						[cheshire.core :as ch]))

(against-background [(before :facts (do
																			(flush-redis)
																			(flush-dynamo-tables)
																			(flush-es-indexes)
																			(bootstrap-bills)))]
	(fact "Make a websocket connection to the websocket notification endpoint")

	(fact "Retrieve users notifications"
		(let [{body :body} (pav-req :put "/user" test-user)
					{token :token user_id :user_id} (ch/parse-string body true)
					notification-events [{:type "comment" :bill_id "s1182-114" :user_id user_id :timestamp 1446479124991 :comment_id "comment:1"
																:bill_title "A bill to exempt application of JSA attribution rule in case of existing agreements."
																:score 0 :body "Comment text goes here!!" :notification_id "10"}
															 {:type "vote" :bill_id "s1182-114" :user_id user_id
																:bill_title "A bill to exempt application of JSA attribution rule in case of existing agreements."
																:timestamp 1446462364297 :notification_id "11"}]
					_ (persist-notification-event notification-events)
					{status :status body :body} (pav-req :get "/user/notifications" token {})
					{next-page :next-page results :results} (ch/parse-string body true)]
			status => 200
			next-page => 0
			results => (contains notification-events)))

	(fact "Retrieve user notifications, mark notification as read"
		(let [{body :body} (pav-req :put "/user" test-user)
					{token :token user_id :user_id} (ch/parse-string body true)
					notification-events [{:type "comment" :bill_id "s1182-114" :user_id user_id :timestamp 1446479124991 :comment_id "comment:1"
																:bill_title "A bill to exempt application of JSA attribution rule in case of existing agreements."
																:score 0 :body "Comment text goes here!!" :notification_id "10"}
															 {:type "vote" :bill_id "s1182-114" :user_id user_id
																:bill_title "A bill to exempt application of JSA attribution rule in case of existing agreements."
																:timestamp 1446462364297 :notification_id "11"}]
					_ (persist-notification-event notification-events)
					{status :status} (pav-req :post "/user/notification/10/mark" token {})
					{body :body} (pav-req :get "/user/notifications" token {})
					{next-page :next-page results :results} (ch/parse-string body true)]
			status => 201
			next-page => 0
			(first results) => (contains {:read true})))

	(fact "Retrieving user notifications without Authentication token, results in 401"
		(let [{status :status} (pav-req :get "/user/notifications" "token" {})]
			status => 401)))
