(ns com.pav.api.events.handler
  (:require [com.pav.api.dynamodb.user :as du]
            [com.pav.api.notifications.ws-handler :as ws]
            [com.pav.api.events.vote :refer :all]
            [com.pav.api.events.user :refer :all]
            [com.pav.api.events.comment :refer :all]
            [clojure.tools.logging :as log]))

(defprotocol EventHandler
  (process-event [evt] "Handles event types"))

(defn- publish-to-timeline [evt]
  (try
    (du/add-event-to-usertimeline evt)
    (catch Exception e (log/error (str "Error occured publishing " (type evt) " to Timeline" evt) e))))

(defn- publish-to-newsfeed [evt]
  (try
    (du/add-event-to-user-notifications evt)
    (catch Exception e (log/error (str "Error occured publishing " (type evt) " to Newsfeed" evt) e))))

(defn- publish-batch-to-newsfeed [batch]
  (try
    (du/publish-batch-to-feed batch)
    (catch Exception e (log/error (str "Error occured publishing " (type (first batch)) " to Newsfeed" batch) e))))

(extend-type com.pav.api.events.vote.VoteTimelineEvent
  EventHandler
  (process-event [evt]
    (publish-to-timeline evt)))

(extend-type com.pav.api.events.vote.VoteNotificationEvent
  EventHandler
  (process-event [evt]
    (try
      (publish-to-newsfeed evt)
      (ws/publish-notification evt)
      (catch Exception e (log/error (str "Error occured publishing VoteNotificationEvent " evt) e)))))

(extend-type com.pav.api.events.vote.VoteNewsFeedEvent
  EventHandler
  (process-event [{:keys [user_id] :as evt}]
    (publish-batch-to-newsfeed
      (map #(assoc evt :user_id (:user_id %) :voter_id user_id) (du/user-followers user_id)))))

(extend-type com.pav.api.events.user.FollowingUserTimelineEvent
  EventHandler
  (process-event [evt]
    (publish-to-timeline evt)))

(extend-type com.pav.api.events.comment.CommentTimeLineEvent
  EventHandler
  (process-event [evt]
    (publish-to-timeline evt)))

(extend-type com.pav.api.events.comment.CommentNewsFeedEvent
  EventHandler
  (process-event [{:keys [user_id] :as evt}]
    (publish-batch-to-newsfeed
      (map #(assoc evt :user_id (:user_id %) :author user_id) (du/user-followers user_id)))))

(extend-type com.pav.api.events.comment.CommentScoreTimelineEvent
  EventHandler
  (process-event [evt]
    (publish-to-timeline evt)))