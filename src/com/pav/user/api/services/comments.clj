(ns com.pav.user.api.services.comments
  (:require [com.pav.user.api.dynamodb.comments :as dc]
            [com.pav.user.api.redis.redis :as redis]
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

(defn create-comments-key []
  (.toString (UUID/randomUUID)))

(defn persist-comment [comment]
  (dc/create-comment comment))

(defn create-bill-comment [comment user]
  (let [author user
        new-comment-id (create-comments-key)
        comment-with-img-url (assoc comment :author_img_url (:img_url user))
        new-dynamo-comment (new-dynamo-comment new-comment-id author comment-with-img-url)]
    (persist-comment new-dynamo-comment)
    (redis/publish-bill-comment new-dynamo-comment)
    {:record new-dynamo-comment}))

(defn create-bill-comment-reply [comment-id reply user]
  (let [author user
        new-comment-id (create-comments-key)
        reply-with-img-url (assoc reply :author_img_url (:img_url user))
        new-dynamo-comment (-> (new-dynamo-comment new-comment-id author reply-with-img-url)
                             (assoc :parent_id comment-id))]
    (persist-comment new-dynamo-comment)
    ;;Publish necessary notification messages to redis for timeline worker.
    (redis/publish-bill-comment new-dynamo-comment)
    (redis/publish-bill-comment-reply new-dynamo-comment)
    (redis/publish-bill-comment-email-reply new-dynamo-comment)
    {:record new-dynamo-comment}))

(defn get-bill-comments
  [user_id bill-id & {:keys [sort-by last_comment_id]
                      :or {sort-by :highest-score last_comment_id nil}}]
  (dc/get-bill-comments bill-id :user_id user_id :sort-by sort-by :last_comment_id last_comment_id))

(defn score-bill-comment [user_id comment-id operation]
  (dc/score-comment comment-id user_id operation)
  (redis/publish-scoring-comment-evt (dc/get-bill-comment comment-id) user_id operation))

(defn revoke-liked-comment [user_id comment_id]
  (dc/remove-liked-comment user_id comment_id))

(defn revoke-disliked-comment [user_id comment_id]
  (dc/remove-disliked-comment user_id comment_id))

(defn get-top-comments [bill-id user_id]
  ((memo/ttl dc/get-top-comments :ttl/threshold 1800000) bill-id user_id))
