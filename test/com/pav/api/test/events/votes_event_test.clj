(ns com.pav.api.test.events.votes-event-test
  (:use midje.sweet)
  (:require [com.pav.api.events.vote :as v]))

(fact "Create VoteTimelineEvent, When vote record is valid, Then return new VoteTimelineEvent"
  (v/create-timeline-vote-event {:bill_id "hr2-114" :timestamp 1020303 :user_id "user101" :vote-id "vote101"})
    => (just {:event_id anything :bill_id "hr2-114" :timestamp 1020303 :user_id "user101" :vote-id "vote101" :type "vote"}))

(fact "Create VoteTimelineEvent, When vote record is missing vote-id, Then throw Exception"
  (v/create-timeline-vote-event {:bill_id "hr2-114" :bill_title "bill title" :timestamp 1020303 :user_id "user101"})
    => (throws Exception))

(fact "Create VoteNotificationEvent, When vote record is valid, Then return new VoteNotificationEvent"
  (v/create-notification-vote-event {:bill_id "hr2-114" :bill_title "bill title" :timestamp 1020303
                                     :user_id "user101" :vote-id "vote101"})
    => (just {:notification_id anything :bill_id "hr2-114" :bill_title "bill title" :timestamp 1020303
              :user_id "user101" :vote-id "vote101" :read false :type "vote"}))

(fact "Create VoteNotificationEvent, When vote record is missing bill_title, Then throw Exception"
  (v/create-notification-vote-event {:bill_id "hr2-114" :timestamp 1020303 :user_id "user101" :vote-id "vote101"})
  => (throws Exception))
