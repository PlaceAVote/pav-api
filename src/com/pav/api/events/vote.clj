(ns com.pav.api.events.vote
  (:require [schema.core :as s])
  (:import (java.util UUID)))

(s/defrecord VoteTimelineEvent
  [event_id  :- s/Str
   type      :- s/Str
   bill_id   :- s/Str
   vote-id   :- s/Str
   user_id   :- s/Str
   timestamp :- Long
   read      :- s/Bool])

(s/defn ^:always-validate create-timeline-vote-event :- VoteTimelineEvent
  "Accepts a user vote, and returns an event suitable for publishing to the users timeline."
  ([event_id {:keys [bill_id vote-id user_id timestamp]}]
    (VoteTimelineEvent. event_id "vote" bill_id vote-id user_id timestamp false))
  ([event]
    (create-timeline-vote-event (.toString (UUID/randomUUID)) event)))

(s/defrecord VoteNotificationEvent
  [notification_id  :- s/Str
   type             :- s/Str
   bill_id          :- s/Str
   bill_title       :- s/Str
   vote-id          :- s/Str
   user_id          :- s/Str
   timestamp        :- Long
   read             :- s/Bool])

(s/defn ^:always-validate create-notification-vote-event :- VoteNotificationEvent
  "Accepts a user vote, and returns an event suitable for publishing to the users notifications."
  ([notification_id {:keys [bill_id bill_title vote-id user_id timestamp]}]
    (VoteNotificationEvent. notification_id "vote" bill_id bill_title vote-id user_id timestamp false))
  ([event]
    (create-notification-vote-event (.toString (UUID/randomUUID)) event)))
