(ns com.pav.api.services.comments
  (:require [com.pav.api.dynamodb.comments :as dc]
            [com.pav.api.dynamodb.user :as du]
            [com.pav.api.elasticsearch.user :as eu]
            [com.pav.api.events.comment :refer [create-comment-timeline-event create-comment-newsfeed-event
                                                create-comment-score-timeline-event
                                                create-comment-reply-notification-event
                                                create-comment-reply-wsnotification-event]]
            [com.pav.api.events.handler :refer [process-event]]
            [clojure.core.memoize :as memo])
  (:import (java.util UUID Date)))

(defn new-dynamo-comment [comment-id author comment]
  (-> comment
    (assoc :comment_id comment-id)
    (assoc :id comment-id)
    (assoc :timestamp (.getTime (Date.)))
    (assoc :parent_id nil)
    (assoc :has_children false)
    (assoc :score 0)
    (assoc :author (:user_id author)
           :author_first_name (:first_name author)
           :author_last_name (:last_name author))))

(defn assoc-bill-comment-count [comment]
  (dc/assoc-bill-comment-count comment))

(defn get-comment-count [bill_id]
  (dc/get-comment-count bill_id))

(defn create-comments-key []
  (.toString (UUID/randomUUID)))

(defn persist-comment [comment]
  (dc/create-comment comment))

(defn- publish-comment-reply-notifications [{:keys [bill_id parent_id] :as comment}]
  (let [notification_id (.toString (UUID/randomUUID))
        parent_user_id (:author (dc/get-bill-comment parent_id))
        bill_title (eu/get-priority-bill-title (eu/get-bill bill_id))]
    (process-event (create-comment-reply-notification-event notification_id
                     (assoc comment :user_id parent_user_id)))
    (process-event (create-comment-reply-wsnotification-event notification_id
                     (assoc comment :user_id parent_user_id :bill_title bill_title)))))

(defn- publish-comment-events
  "Takes a new comment then generates the relevant event types and processes them."
  [{:keys [parent_id] :as comment}]
  (process-event (create-comment-timeline-event comment))
  (and (nil? parent_id) (process-event (create-comment-newsfeed-event comment)))
  (and parent_id (publish-comment-reply-notifications comment)))

(defn create-bill-comment [comment user]
  (let [author user
        new-comment-id (create-comments-key)
        comment-with-img-url (assoc comment :author_img_url (:img_url user))
        new-dynamo-comment (new-dynamo-comment new-comment-id author comment-with-img-url)]
    (persist-comment new-dynamo-comment)
    (publish-comment-events new-dynamo-comment)
    {:record new-dynamo-comment}))

(defn create-bill-comment-reply [comment-id reply user]
  (let [author user
        new-comment-id (create-comments-key)
        reply-with-img-url (assoc reply :author_img_url (:img_url user))
        new-dynamo-comment (-> (new-dynamo-comment new-comment-id author reply-with-img-url)
                               (assoc :parent_id comment-id))]
    (persist-comment new-dynamo-comment)
    (publish-comment-events new-dynamo-comment)
    {:record new-dynamo-comment}))

(defn get-bill-comments
  [user_id bill-id & {:keys [sort-by last_comment_id]
                      :or {sort-by :highest-score last_comment_id nil}}]
  (dc/get-bill-comments bill-id :user_id user_id :sort-by sort-by :last_comment_id last_comment_id))

(defn score-bill-comment [user_id comment-id operation]
  (dc/score-comment comment-id user_id operation)
  (process-event
    (create-comment-score-timeline-event operation (assoc (dc/get-bill-comment comment-id)
                                                     :timestamp (.getTime (Date.))))))

(defn revoke-liked-comment [user_id comment_id]
  (dc/remove-liked-comment user_id comment_id))

(defn revoke-disliked-comment [user_id comment_id]
  (dc/remove-disliked-comment user_id comment_id))

(defn get-top-comments [bill-id user_id]
  ((memo/ttl dc/get-top-comments :ttl/threshold 1800000) bill-id user_id))
