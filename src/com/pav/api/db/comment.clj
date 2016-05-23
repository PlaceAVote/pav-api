(ns com.pav.api.db.comment
  (:require [com.pav.api.db.db :as db]
            [com.pav.api.db.tables :as t]
            [clojure.java.jdbc :as sql]
            [clojure.tools.logging :as log]
            [com.pav.api.utils.utils :refer [sstr]]
            [com.pav.api.db.common :refer [extract-value unclobify]])
  (:import (java.util Date)))

(defn- insert-comment [d comment]
  (->>
    (select-keys comment [:user_id :parent_id :body :has_children :score :created_at
                          :updated_at :deleted :old_user_id :old_comment_id :old_parent_id])
    (sql/insert! d t/comments-table)
    extract-value))

(defn create-bill-comment [comment]
  (try
    (sql/with-db-transaction [d db/db]
      (let [id (insert-comment d comment)]
        (sql/insert! d t/user-bill-comments-table {:comment_id id :bill_id (:bill_id comment)})
        id))
    (catch Throwable e (log/error "Error occured writing bill comment for " comment " with " e))))

(defn create-issue-comment [comment]
  (try
    (sql/with-db-transaction [d db/db]
      (let [id (insert-comment d comment)]
        (sql/insert! d t/user-issue-comments-table {:comment_id id :issue_id (:issue_id comment)})
        id))
    (catch Throwable e (log/error "Error occured writing issue comment for " comment " with " e))))

(defn get-comment-by-old-id
  "Temporary function to retrieve comments by dynamodb comment_id"
  [comment_id]
  (first
    (sql/query db/db [(sstr "SELECT * FROM " t/comments-table " WHERE old_comment_id = ? LIMIT 1") comment_id]
      {:row-fn unclobify})))

(defn- update-comment [props comment_id]
  (log/info "Updating comment " comment_id " with " props)
  (sql/with-db-transaction [d db/db]
    (sql/update! d t/comments-table props ["id=?" comment_id])))

(defn update-bill-comment [props comment_id]
  (update-comment props comment_id))

(defn update-issue-comment [props comment_id]
  (update-comment props comment_id))

(defn mark-bill-comment-for-deletion [comment_id]
  (update-bill-comment {:deleted true :updated_at (.getTime (Date.))} comment_id))

(defn mark-issue-comment-for-deletion [comment_id]
  (update-bill-comment {:deleted true :updated_at (.getTime (Date.))} comment_id))

(defn- update-comment-score [conn comment_id op]
  (let [i (case op :like 1 :dislike -1 true 1 false -1)]
    (sql/execute! conn [(sstr "UPDATE " t/comments-table " SET score=score + ? WHERE id=?") i comment_id])))

(defn insert-user-comment-scoring-record [conn scoring-record]
  (sql/insert! conn t/user-comment-scores-table scoring-record))

(defn- delete-user-bill-comment-ref [conn comment_id user_id]
  (sql/delete! conn t/user-comment-scores-table ["comment_id=? AND user_id=?" comment_id user_id]))

(defn score-comment [scoring-record]
  (sql/with-db-transaction [d db/db]
    (update-comment-score d (:comment_id scoring-record) (:liked scoring-record))
    (insert-user-comment-scoring-record d scoring-record)))

(defn revoke-liked-comment-score [comment_id user_id]
  (log/info "Removing bill comment score for User " user_id " and Comment " comment_id)
  (sql/with-db-transaction [d db/db]
    (update-comment-score d comment_id :dislike)
    (delete-user-bill-comment-ref d comment_id user_id)))

(defn revoke-disliked-comment-score [comment_id user_id]
  (log/info "Removing bill comment score for User " user_id " and Comment " comment_id)
  (sql/with-db-transaction [d db/db]
    (update-comment-score d comment_id :like)
    (delete-user-bill-comment-ref d comment_id user_id)))

(defn get-user-bill-comment-score [comment_id user_id]
  (first
   (sql/query db/db [(sstr "SELECT * FROM " t/user-comment-scores-table " WHERE user_id=? AND comment_id=?") user_id comment_id])))

(defn get-bill-comment-score-by-old-ids [old_comment_id old_user_id]
  (first
   (sql/query db/db [(sstr "SELECT * FROM " t/user-comment-scores-table " WHERE old_user_id=? AND old_comment_id=?") old_user_id old_comment_id])))
