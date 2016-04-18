(ns com.pav.api.test.timeline.timeline-test
	(:use midje.sweet)
	(:require [com.pav.api.test.utils.utils :refer [make-request parse-response-body
                                                  flush-dynamo-tables
                                                  flush-redis
                                                  persist-timeline-event
                                                  flush-es-indexes
                                                  bootstrap-bills-and-metadata
                                                  new-pav-user
                                                  pav-reqv2]]))

(against-background [(before :contents (do 
                                         (flush-redis)
																			   (flush-dynamo-tables)
																			   (flush-es-indexes)
																			   (bootstrap-bills-and-metadata)))]

	(fact "Try Retrieving users profile timeline with invalid token"
		(let [{status :status} (pav-reqv2 :get "/user/me/timeline" "rubbish token" {})]
			status => 401))

  (fact "Retrieve current users activity timeline"
		(let [{body :body} (pav-reqv2 :put "/user" (new-pav-user))
					{token :token user_id :user_id} body
					timeline-events [{:type "comment" :bill_id "s1182-114" :user_id user_id :timestamp 1446479124991 :comment_id "comment:1"
														:score 0 :body "Comment text goes here!!" :bill_title nil}
													 {:type "vote" :bill_id "s1182-114" :user_id user_id :timestamp  1446462364297 :bill_title nil}]
					_ (persist-timeline-event timeline-events)
					{status :status body :body} (pav-reqv2 :get "/user/me/timeline" token {})
					{last_timestamp :last_timestamp results :results} body]
			status => 200
			last_timestamp => (:timestamp (last results))))

  (fact "Retrieve current user activity timeline, When from equals the first records timestamp, Then only return the second timeline event"
    (let [{body :body} (pav-reqv2 :put "/user" (new-pav-user))
          {token :token user_id :user_id} body
          timeline-events [{:type       "comment" :bill_id "s1182-114" :user_id user_id :timestamp 1446479124991 :comment_id "comment:1"
                            :bill_title nil
                            :score      0 :body "Comment text goes here!!"}
                           {:type       "vote" :bill_id "s1182-114" :user_id user_id
                            :bill_title nil
                            :timestamp  1446462364297}]
          _ (persist-timeline-event timeline-events)
          {status :status body :body} (pav-reqv2 :get "/user/me/timeline?from=1446479124991" token {})
          {last_timestamp :last_timestamp results :results} body]
      status => 200
      last_timestamp => (:timestamp (last results))))

  (fact "Retrieve a users activity timeline"
		(let [{body :body} (pav-reqv2 :put "/user" (new-pav-user))
					{token :token} body
					timeline-events [{:type       "comment" :bill_id "s1182-114" :user_id "user102" :timestamp 1446479124991 :comment_id "comment:1"
														:bill_title nil
														:score      0 :body "Comment text goes here!!"}
													 {:type       "vote" :bill_id "s1182-114" :user_id "user102"
														:bill_title nil
														:timestamp  1446462364297}]
					_ (persist-timeline-event timeline-events)
					{status :status body :body} (pav-reqv2 :get "/user/user102/timeline" token {})
          {last_timestamp :last_timestamp results :results} body]
      status => 200
      last_timestamp => (:timestamp (last results))))

  (fact "Retrieve a users activity timeline, When from equals the first records timestamp, Then only return the second timeline event"
    (let [{body :body} (pav-reqv2 :put "/user" (new-pav-user))
          {token :token} body
          timeline-events [{:type       "comment" :bill_id "s1182-114" :user_id "user102" :timestamp 1446479124991 :comment_id "comment:1"
                            :bill_title nil
                            :score      0 :body "Comment text goes here!!"}
                           {:type       "vote" :bill_id "s1182-114" :user_id "user102"
                            :bill_title nil
                            :timestamp  1446462364297}]
          _ (persist-timeline-event timeline-events)
          {status :status body :body} (pav-reqv2 :get "/user/user102/timeline?from=1446479124991" token {})
          {last_timestamp :last_timestamp results :results} body]
      status => 200
      last_timestamp => (:timestamp (last results)))))

