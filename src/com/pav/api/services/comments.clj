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
            [com.pav.api.domain.comment :refer [new-bill-comment]])
  (:import (java.util UUID Date)))

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

(defn persist-comment [comment]
  (dbwrapper/create-bill-comment comment))

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

(defn- assoc-user-metadata-with-comment [comment {:keys [img_url first_name last_name]}]
  (assoc comment
    :author_img_url img_url :author_first_name first_name :author_last_name last_name))

(defn update-bill-comment [payload comment_id]
  (let [{author :author :as comment} (dbwrapper/update-bill-comment comment_id payload)]
    (assoc-user-metadata-with-comment comment (du/get-user-by-id author))))

(defn delete-bill-comment [comment_id user_id]
  (dbwrapper/mark-bill-comment-for-deletion comment_id user_id))

(defn create-bill-comment
  "Create Bill User Comment"
  ([comment user]
   (let [author (du/get-user-by-id (:user_id user))
         new-comment (new-bill-comment comment author)
         comment-with-user-meta (assoc-user-metadata-with-comment new-comment author)]
     (log/info "Persisting new comment " comment-with-user-meta)
     (persist-comment new-comment)
     (publish-comment-events comment-with-user-meta)
     {:record comment-with-user-meta}))
  ([parent_id comment user]
    (create-bill-comment (assoc comment :parent_id parent_id) user)))

(defn create-user-issue-comment [comment user_id]
  (let [user (du/get-user-by-id user_id)
        new-dynamo-comment (new-issue-comment comment user)]
    (persist-issue-comment new-dynamo-comment)
    (assoc new-dynamo-comment :liked false :disliked false)))

(defn update-user-issue-comment [comment_id payload]
  (dc/update-user-issue-comment (:body payload) comment_id))

(defn delete-user-issue-comment [comment_id]
  (dc/mark-user-issue-for-deletion comment_id))

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
