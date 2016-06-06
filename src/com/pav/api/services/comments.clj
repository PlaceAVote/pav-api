(ns com.pav.api.services.comments
  (:require [com.pav.api.dynamodb.comments :as dc]
            [com.pav.api.dbwrapper.comment :as dbwrapper]
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
            [com.pav.api.dynamodb.user :as du]
            [com.pav.api.domain.comment :refer [new-bill-comment new-comment-score new-issue-comment]])
  (:import (java.util UUID Date)))

(defn assoc-bill-comment-count [comment]
  (dc/assoc-bill-comment-count comment))

(defn get-comment-count [bill_id]
  (dc/get-comment-count bill_id))

(defn persist-comment [comment]
  (dbwrapper/create-bill-comment comment))

(defn persist-issue-comment [comment]
  (dbwrapper/create-issue-comment comment))

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

(defn- assoc-user-metadata-with-comment [comment {:keys [img_url first_name last_name]}]
  (assoc comment
    :author_img_url img_url :author_first_name first_name :author_last_name last_name))

(defn update-bill-comment [payload comment_id]
  (let [{author :author :as comment} (dbwrapper/update-bill-comment comment_id payload)]
    (assoc-user-metadata-with-comment comment (du/get-user-by-id author))))

(defn delete-bill-comment [comment_id user_id]
  (dbwrapper/mark-bill-comment-for-deletion comment_id user_id))

(defn- bill-comment-response [comment]
  {:record (assoc comment :liked false :disliked false :replies [])})

(defn- issue-comment-response [comment]
  (assoc comment :liked false :disliked false :updated_at (:timestamp comment)))

(defn create-bill-comment
  "Create Bill User Comment"
  ([comment user]
   (let [author (du/get-user-by-id (:user_id user))
         new-comment (new-bill-comment comment author)
         comment-with-user-meta (assoc-user-metadata-with-comment new-comment author)]
     (log/info "Persisting new comment " comment-with-user-meta)
     (persist-comment new-comment)
     (publish-comment-events comment-with-user-meta)
     (bill-comment-response comment-with-user-meta)))
  ([parent_id comment user]
    (create-bill-comment (assoc comment :parent_id parent_id) user)))

(defn create-user-issue-comment [comment user_id]
  (let [user (du/get-user-by-id user_id)
        new-dynamo-comment (new-issue-comment comment user)
        comment_with-user-metadata (assoc-user-metadata-with-comment new-dynamo-comment user)]
    (persist-issue-comment new-dynamo-comment)
    (issue-comment-response comment_with-user-metadata)))

(defn update-user-issue-comment [comment_id payload]
  (let [updated (dbwrapper/update-user-issue-comment payload comment_id)
        user (du/get-user-by-id (:author updated))]
    (->
      (assoc-user-metadata-with-comment updated user)
      issue-comment-response)))

(defn delete-user-issue-comment [comment_id user_id]
  (let [user (du/get-user-by-id user_id)
        updated (dbwrapper/mark-user-issue-for-deletion comment_id)]
    (->
      (assoc-user-metadata-with-comment updated user)
      issue-comment-response)))

(defn get-bill-comments
  [user_id bill-id & {:keys [sort-by last_comment_id]
                      :or {sort-by :highest-score last_comment_id nil}}]
  (dc/get-user-bill-comments bill-id :user_id user_id :sort-by sort-by :last_comment_id last_comment_id))

(defn score-bill-comment [user_id comment-id operation]
  (dbwrapper/score-bill-comment (new-comment-score comment-id user_id (case operation :like true :dislike false)))
  (process-event
    (create-comment-score-timeline-event
      operation user_id (assoc (dc/get-bill-comment comment-id) :timestamp (.getTime (Date.))))))

(defn revoke-liked-comment [user_id comment_id]
  (dbwrapper/revoke-liked-bill-comment-score comment_id user_id))

(defn revoke-disliked-comment [user_id comment_id]
  (dbwrapper/revoke-disliked-bill-comment-score comment_id user_id))

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

(defn bill-comment-exists? [comment_id]
  (-> (dc/get-bill-comment comment_id) seq nil? false?))

(defn get-user-issue-comments [issue_id user_id & {:keys [sort-by last_comment_id]
                                                   :or   {sort-by :highest-score last_comment_id nil}}]
  (dc/get-user-issue-comments issue_id :user_id user_id :sort-by sort-by :last_comment_id last_comment_id))

(defn score-issue-comment [user_id comment_id operation]
  (dbwrapper/score-issue-comment (new-comment-score comment_id user_id (case operation :like true :dislike false))))

(defn revoke-issue-score [user_id comment_id operation]
  (case operation
    :like (dbwrapper/revoke-liked-issue-comment-score comment_id user_id)
    :dislike (dbwrapper/revoke-disliked-issue-comment-score comment_id user_id)))
