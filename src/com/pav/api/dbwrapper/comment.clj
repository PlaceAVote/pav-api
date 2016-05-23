(ns com.pav.api.dbwrapper.comment
  (:require [com.pav.api.dynamodb.comments :as dynamo]
            [com.pav.api.db.comment :as sql]
            [com.pav.api.db.user :as u-sql]
            [com.pav.api.db.issue :as i-sql]
            [com.pav.api.dbwrapper.helpers :refer [with-sql-backend bigint->long]]
            [com.pav.api.utils.utils :refer [prog1]])
  (:import (java.util Date)))

(defn dynamodb->sql-comment [comment]
  (let [c (assoc comment
            :issue_id (i-sql/get-user-issue-by-old-id (:issue_id comment))
            :old_comment_id (:comment_id comment)
            :old_user_id (:author comment)
            :user_id (:user_id (u-sql/get-user-by-old-id (:author comment)))
            :created_at (bigint->long (:timestamp comment))
            :updated_at (bigint->long (:timestamp comment))
            :score (bigint->long (:score comment)))]
    (if-let [p (:parent_id c)]
      (assoc c :parent_id (:id (sql/get-comment-by-old-id p)) :old_parent_id p)
      c)))

(defn dynamo-comment-score->sql-comment-score [{:keys [comment_id user_id liked]}]
  {:old_comment_id comment_id
   :old_user_id    user_id
   :liked          liked
   :user_id        (:user_id (u-sql/get-user-by-old-id user_id))
   :comment_id     (:id (sql/get-comment-by-old-id comment_id))
   :created_at     (.getTime (Date.))
   :updated_at     (.getTime (Date.))})


(defn create-bill-comment [comment]
  (prog1
    (dynamo/create-bill-comment comment)
    (with-sql-backend
      (sql/create-bill-comment (-> comment dynamodb->sql-comment)))))

(defn create-issue-comment [comment]
  (prog1
    (dynamo/create-issue-comment comment)
    (with-sql-backend
      (sql/create-issue-comment (-> comment dynamodb->sql-comment)))))

(defn update-bill-comment [comment_id props]
  (prog1
    (dynamo/update-bill-comment props comment_id)
    (with-sql-backend
      (sql/update-bill-comment props (:id (sql/get-comment-by-old-id comment_id))))))

(defn update-user-issue-comment [updated comment_id]
  (prog1
    (dynamo/update-user-issue-comment updated comment_id)
    (with-sql-backend
      (sql/update-issue-comment updated (:id (sql/get-comment-by-old-id comment_id))))))

(defn mark-bill-comment-for-deletion [comment_id user_id]
  (prog1
    (dynamo/delete-comment comment_id user_id)
    (with-sql-backend
      (sql/mark-bill-comment-for-deletion (:id (sql/get-comment-by-old-id comment_id))))))

(defn mark-user-issue-for-deletion [comment_id]
  (prog1
    (dynamo/mark-user-issue-for-deletion comment_id)
    (with-sql-backend
      (sql/mark-issue-comment-for-deletion (:id (sql/get-comment-by-old-id comment_id))))))

(defn score-bill-comment [scoring-record]
  (prog1
    (dynamo/score-comment scoring-record)
    (with-sql-backend
      (-> scoring-record dynamo-comment-score->sql-comment-score sql/score-comment))))

(defn score-issue-comment [scoring-record]
  (prog1
    (dynamo/score-issue-comment scoring-record)
    (with-sql-backend
      (-> scoring-record dynamo-comment-score->sql-comment-score sql/score-comment))))

(defn revoke-liked-bill-comment-score [comment_id user_id]
  (prog1
    (dynamo/remove-liked-comment user_id comment_id)
    (with-sql-backend
      (sql/revoke-liked-comment-score
        (:id (sql/get-comment-by-old-id comment_id))
        (:user_id (u-sql/get-user-by-old-id user_id))))))

(defn revoke-liked-issue-comment-score [comment_id user_id]
  (prog1
    (dynamo/revoke-issue-score user_id comment_id :like)
    (with-sql-backend
      (sql/revoke-liked-comment-score
        (:id (sql/get-comment-by-old-id comment_id))
        (:user_id (u-sql/get-user-by-old-id user_id))))))

(defn revoke-disliked-bill-comment-score [comment_id user_id]
  (prog1
    (dynamo/remove-disliked-comment user_id comment_id)
    (with-sql-backend
      (sql/revoke-disliked-comment-score
        (:id (sql/get-comment-by-old-id comment_id))
        (:user_id (u-sql/get-user-by-old-id user_id))))))

(defn revoke-disliked-issue-comment-score [comment_id user_id]
  (prog1
    (dynamo/revoke-issue-score user_id comment_id :dislike)
    (with-sql-backend
      (sql/revoke-disliked-comment-score
        (:id (sql/get-comment-by-old-id comment_id))
        (:user_id (u-sql/get-user-by-old-id user_id))))))