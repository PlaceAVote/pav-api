(ns com.pav.api.events.handler
  (:require [com.pav.api.dynamodb.user :as du]
            [com.pav.api.notifications.ws-handler :as ws]
            [com.pav.api.events.vote :refer :all]
            [com.pav.api.events.user :refer :all]
            [clojure.tools.logging :as log]))

(defprotocol EventHandler
  (process-event [evt] "Handles event types"))

(extend-type com.pav.api.events.vote.VoteTimelineEvent
  EventHandler
  (process-event [evt]
    (try
      (du/add-event-to-usertimeline evt)
      (catch Exception e (log/error ("Error occured publishing VoteTimelineEvent " evt) e)))))

(extend-type com.pav.api.events.vote.VoteNotificationEvent
  EventHandler
  (process-event [evt]
    (try
      (du/add-event-to-user-notifications evt)
      (ws/publish-notification evt)
      (catch Exception e (log/error ("Error occured publishing VoteNotificationEvent " evt) e)))))

(extend-type com.pav.api.events.vote.VoteNewsFeedEvent
  EventHandler
  (process-event [{:keys [user_id] :as evt}]
    (try
      (du/publish-batch-to-feed
        (map #(assoc evt :user_id (:user_id %) :voter_id user_id) (du/user-followers user_id)))
      (catch Exception e (log/error ("Error occured publishing VoteNewsfeedEvent " evt) e)))))

(extend-type com.pav.api.events.user.FollowingUserTimelineEvent
  EventHandler
  (process-event [evt]
    (try
      (du/add-event-to-usertimeline evt)
      (catch Exception e (log/error (str "Error occured publishing FollowingUserTimelineEvent " evt) e)))))
