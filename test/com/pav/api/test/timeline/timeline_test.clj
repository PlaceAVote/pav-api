(ns com.pav.api.test.timeline.timeline-test
	(:use midje.sweet)
	(:require [cheshire.core :as ch]
						[com.pav.api.test.utils.utils :refer [make-request parse-response-body
																											 flush-dynamo-tables
																											 flush-redis
																											 persist-timeline-event
																											 flush-es-indexes
																											 bootstrap-bills-and-metadata
																											 test-user
																											 pav-req]]))

(against-background [(before :facts (do
																			(flush-redis)
																			(flush-dynamo-tables)
																			(flush-es-indexes)
																			(bootstrap-bills-and-metadata)))]
	(fact "Try Retrieving users profile timeline with invalid token"
		(let [{status :status} (pav-req :get "/user/me/timeline" "rubbish token" {})]
			status => 401))

	(fact "Retrieve current users activity timeline"
		(let [{body :body} (pav-req :put "/user" test-user)
					{token :token user_id :user_id} (ch/parse-string body true)
					timeline-events [{:type "comment" :bill_id "s1182-114" :user_id user_id :timestamp 1446479124991 :comment_id "comment:1"
														:score 0 :body "Comment text goes here!!" :bill_title nil}
													 {:type "vote" :bill_id "s1182-114" :user_id user_id :timestamp  1446462364297 :bill_title nil}]
					_ (persist-timeline-event timeline-events)
					{status :status body :body} (pav-req :get "/user/me/timeline" token {})
					{last_timestamp :last_timestamp results :results} (ch/parse-string body true)]
			status => 200
			last_timestamp => (:timestamp (last results))
			results => (contains timeline-events)))

  (fact "Retrieve current user activity timeline, When from equals the first records timestamp, Then only return the second timeline event"
    (let [{body :body} (pav-req :put "/user" test-user)
          {token :token user_id :user_id} (ch/parse-string body true)
          timeline-events [{:type       "comment" :bill_id "s1182-114" :user_id user_id :timestamp 1446479124991 :comment_id "comment:1"
                            :bill_title nil
                            :score      0 :body "Comment text goes here!!"}
                           {:type       "vote" :bill_id "s1182-114" :user_id user_id
                            :bill_title nil
                            :timestamp  1446462364297}]
          _ (persist-timeline-event timeline-events)
          {status :status body :body} (pav-req :get "/user/me/timeline?from=1446479124991" token {})
          {last_timestamp :last_timestamp results :results} (ch/parse-string body true)]
      status => 200
      last_timestamp => 1446462364297
      results => (contains (second timeline-events))))

	(fact "Retrieve a users activity timeline"
		(let [{body :body} (pav-req :put "/user" test-user)
					{token :token} (ch/parse-string body true)
					timeline-events [{:type       "comment" :bill_id "s1182-114" :user_id "user102" :timestamp 1446479124991 :comment_id "comment:1"
														:bill_title nil
														:score      0 :body "Comment text goes here!!"}
													 {:type       "vote" :bill_id "s1182-114" :user_id "user102"
														:bill_title nil
														:timestamp  1446462364297}]
					_ (persist-timeline-event timeline-events)
					{status :status body :body} (pav-req :get "/user/user102/timeline" token {})
          {last_timestamp :last_timestamp results :results} (ch/parse-string body true)]
      status => 200
      last_timestamp => (:timestamp (last results))
      results => (contains timeline-events)))

  (fact "Retrieve a users activity timeline, When from equals the first records timestamp, Then only return the second timeline event"
    (let [{body :body} (pav-req :put "/user" test-user)
          {token :token} (ch/parse-string body true)
          timeline-events [{:type       "comment" :bill_id "s1182-114" :user_id "user102" :timestamp 1446479124991 :comment_id "comment:1"
                            :bill_title nil
                            :score      0 :body "Comment text goes here!!"}
                           {:type       "vote" :bill_id "s1182-114" :user_id "user102"
                            :bill_title nil
                            :timestamp  1446462364297}]
          _ (persist-timeline-event timeline-events)
          {status :status body :body} (pav-req :get "/user/user102/timeline?from=1446479124991" token {})
          {last_timestamp :last_timestamp results :results} (ch/parse-string body true)]
      status => 200
      last_timestamp => 1446462364297
      results => (contains (second timeline-events)))))

