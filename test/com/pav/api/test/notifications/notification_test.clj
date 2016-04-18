(ns com.pav.api.test.notifications.notification-test
	(:use midje.sweet)
	(:require [com.pav.api.test.utils.utils :refer [pav-reqv2
																									persist-notification-event
																									flush-dynamo-tables
																									flush-redis
																									flush-es-indexes
																									new-pav-user
																									bootstrap-bills-and-metadata]]))

(against-background [(before :contents (do
                                         (flush-redis)
                                         (flush-dynamo-tables)
																			   (flush-es-indexes)
																			   (bootstrap-bills-and-metadata)))]

	(fact "Retrieve users notifications"
		(let [{body :body} (pav-reqv2 :put "/user" (new-pav-user))
					{token :token user_id :user_id} body
					notification-events [{:type "comment" :bill_id "s1182-114" :user_id user_id :timestamp 1446479124991 :comment_id "comment:1"
																:bill_title "A bill to exempt application of JSA attribution rule in case of existing agreements."
																:score 0 :body "Comment text goes here!!" :notification_id "10"}
															 {:type "vote" :bill_id "s1182-114" :user_id user_id
																:bill_title "A bill to exempt application of JSA attribution rule in case of existing agreements."
																:timestamp 1446462364297 :notification_id "11"}]
					_ (persist-notification-event notification-events)
					{status :status body :body} (pav-reqv2 :get "/user/notifications" token {})
					{last_timestamp :last_timestamp} body]
			status => 200
			last_timestamp => 1446462364297))

  (fact "Retrieve users notifications, When from parameter is present, Then return the last record only."
    (let [{body :body} (pav-reqv2 :put "/user" (new-pav-user))
          {token :token user_id :user_id} body
          notification-events [{:type "comment" :bill_id "s1182-114" :user_id user_id :timestamp 1446479124991 :comment_id "comment:1"
                                :bill_title "A bill to exempt application of JSA attribution rule in case of existing agreements."
                                :score 0 :body "Comment text goes here!!" :notification_id "10"}
                               {:type "vote" :bill_id "s1182-114" :user_id user_id
                                :bill_title "A bill to exempt application of JSA attribution rule in case of existing agreements."
                                :timestamp 1446462364297 :notification_id "11"}]
          _ (persist-notification-event notification-events)
          {status :status body :body} (pav-reqv2 :get "/user/notifications?from=1446479124991" token {})
          {last_timestamp :last_timestamp results :results} body]
      status => 200
      last_timestamp => 1446462364297
      (count results) => 1))

	(fact "Retrieve user notifications, mark notification as read"
    (flush-dynamo-tables)
		(let [{body :body} (pav-reqv2 :put "/user" (new-pav-user))
					{token :token user_id :user_id} body
					notification-events [{:type "comment" :bill_id "s1182-114" :user_id user_id :timestamp 1446479124991 :comment_id "comment:1"
																:bill_title "A bill to exempt application of JSA attribution rule in case of existing agreements."
																:score 0 :body "Comment text goes here!!" :notification_id "10"}
															 {:type "vote" :bill_id "s1182-114" :user_id user_id
																:bill_title "A bill to exempt application of JSA attribution rule in case of existing agreements."
																:timestamp 1446462364297 :notification_id "11"}]
					_ (persist-notification-event notification-events)
					{status :status} (pav-reqv2 :post "/user/notification/10/mark" token {})
					{body :body} (pav-reqv2 :get "/user/notifications" token {})
					{last_timestamp :last_timestamp results :results} body]
			status => 201
			last_timestamp => 1446462364297
			(first results) => (contains {:read true})))

	(fact "Retrieving user notifications without Authentication token, results in 401"
		(let [{status :status} (pav-reqv2 :get "/user/notifications" "token" {})]
			status => 401)))
