(ns com.pav.api.services.comments
  (:require [com.pav.api.dynamodb.comments :as dc]
            [com.pav.api.elasticsearch.user :as eu]
            [com.pav.api.events.comment :refer [create-comment-timeline-event create-comment-newsfeed-event
                                                create-comment-score-timeline-event
                                                create-comment-reply-notification-event
                                                create-comment-reply-wsnotification-event
                                                create-comment-reply-email-notification-event]]
            [com.pav.api.events.handler :refer [process-event]]
            [clojure.core.memoize :as memo]
            [clojure.tools.logging :as log]
            [clojure.core.async :refer [go]]
            [com.pav.api.dynamodb.user :as du])
  (:import (java.util UUID Date)))

(defn- new-dynamo-comment [comment-id author comment]
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

(defn- new-issue-comment [comment author]
  (let [timestamp (.getTime (Date.))]
    (-> comment
      (merge {:comment_id        (.toString (UUID/randomUUID))
              :timestamp         timestamp
              :updated_at        timestamp
              :score             0
              :author            (:user_id author)
              :author_first_name (:first_name author)
              :author_last_name  (:last_name author)
              :author_img_url    (:img_url author)
              :deleted           false}))))

(defn assoc-bill-comment-count [comment]
  (dc/assoc-bill-comment-count comment))

(defn get-comment-count [bill_id]
  (dc/get-comment-count bill_id))

(defn create-comments-key []
  (.toString (UUID/randomUUID)))

(defn persist-comment [comment]
  (dc/create-bill-comment comment))

(defn persist-issue-comment [comment]
  (dc/create-issue-comment comment))

(defn- publish-comment-reply-notifications [{:keys [bill_id parent_id author] :as comment}]
  (let [notification_id (.toString (UUID/randomUUID))
        parent_user_id (:author (dc/get-bill-comment parent_id))
        {:keys [email]} (du/get-user-by-id parent_user_id)
        bill_title (eu/get-priority-bill-title (eu/get-bill bill_id))]
    (process-event (create-comment-reply-notification-event notification_id
                     (assoc comment :user_id parent_user_id)))
    (process-event (create-comment-reply-wsnotification-event notification_id
                     (assoc comment :user_id parent_user_id :bill_title bill_title)))
    (when (and email (not (= parent_user_id author)))
      (process-event (create-comment-reply-email-notification-event
                       (assoc comment :bill_title bill_title :email email))))))

(defn- publish-comment-events
  "Takes a new comment then generates the relevant event types and processes them."
  [{:keys [parent_id] :as comment}]
  (go
    (try
      (process-event (create-comment-timeline-event comment))
      (and (nil? parent_id) (process-event (create-comment-newsfeed-event comment)))
      (and parent_id (publish-comment-reply-notifications comment))
      (catch Exception e (log/error "Error Occured processing comment events " e)))))

(defn update-bill-comment [payload comment_id]
  (dc/update-bill-comment (:body payload) comment_id))

(defn delete-bill-comment [comment_id user_id]
  (dc/delete-comment comment_id user_id))

(defn create-bill-comment [comment user]
  (let [{img_url :img_url :as author} (du/get-user-by-id (:user_id user))
        new-comment-id (create-comments-key)
        comment-with-img-url (assoc comment :author_img_url img_url)
        new-dynamo-comment (new-dynamo-comment new-comment-id author comment-with-img-url)]
    (persist-comment new-dynamo-comment)
    (publish-comment-events new-dynamo-comment)
    {:record new-dynamo-comment}))

(defn create-user-issue-comment [comment user_id]
  (let [user (du/get-user-by-id user_id)
        new-dynamo-comment (new-issue-comment comment user)
        ]
    (persist-issue-comment new-dynamo-comment)
    (assoc new-dynamo-comment :liked false :disliked false)))

(defn update-user-issue-comment [comment_id payload]
  (dc/update-user-issue-comment (:body payload) comment_id))

(defn delete-user-issue-comment [comment_id]
  (dc/mark-user-issue-for-deletion comment_id))

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
  (dc/get-user-bill-comments bill-id :user_id user_id :sort-by sort-by :last_comment_id last_comment_id))

(defn score-bill-comment [user_id comment-id operation]
  (dc/score-comment comment-id user_id operation)
  (process-event
    (create-comment-score-timeline-event
      operation user_id (assoc (dc/get-bill-comment comment-id) :timestamp (.getTime (Date.))))))

(defn revoke-liked-comment [user_id comment_id]
  (dc/remove-liked-comment user_id comment_id))

(defn revoke-disliked-comment [user_id comment_id]
  (dc/remove-disliked-comment user_id comment_id))

(defn get-top-comments [bill-id user_id]
  ((memo/ttl dc/get-top-comments :ttl/threshold 1800000) bill-id user_id))

(defn is-author? [comment_id user_id]
  (if-let [{:keys [author]} (dc/get-bill-comment comment_id)]
    (= author user_id)
    false))

(defn is-issue-author? [comment_id user_id]
  (if-let [{:keys [author]} (dc/get-issue-comment comment_id)]
    (= author user_id)
    false))

(defn get-user-issue-comments [issue_id user_id & {:keys [sort-by last_comment_id]
                                                   :or   {sort-by :highest-score last_comment_id nil}}]
  (dc/get-user-issue-comments issue_id :user_id user_id :sort-by sort-by :last_comment_id last_comment_id))

(defn score-issue-comment [user_id comment_id operation]
  (dc/score-issue-comment comment_id user_id operation))

(defn revoke-issue-score [user_id comment_id operation]
  (dc/revoke-issue-score user_id comment_id operation))
