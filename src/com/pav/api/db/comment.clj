(ns com.pav.api.db.comment
  (:require [com.pav.api.db.db :as db]
            [clojure.java.jdbc :as sql]
            [clojure.tools.logging :as log]
            [com.pav.api.utils.utils :as u]
            [com.pav.api.db.common :refer [extract-value unclobify]])
  (:import (java.util Date)))

(defn create-bill-comment [comment]
  (try
    (sql/with-db-transaction [d db/db]
      (let [comment-payload (select-keys comment [:user_id :parent_id :body :has_children :score :created_at
                                                  :updated_at :deleted :old_user_id :old_comment_id :old_parent_id])
            id (extract-value (sql/insert! d "comments" comment-payload))]
        (log/info "Persisted comment " comment-payload)
        (sql/insert! d "user_bill_comments" {:comment_id id :bill_id (:bill_id comment)})
        id))))

(defn get-bill-comment-by-old-id
  "Temporary function to retrieve comments by dynamodb comment_id"
  [comment_id]
  (first
    (sql/query db/db ["SELECT * FROM comments WHERE old_comment_id = ? LIMIT 1" comment_id]
      {:row-fn unclobify})))

(defn update-bill-comment [props comment_id]
  (log/info "Updating comment " comment_id " with " props)
  (sql/with-db-transaction [d db/db]
    (sql/update! d "comments" props ["id=?" comment_id])))

(defn mark-bill-comment-for-deletion [comment_id]
  (update-bill-comment {:deleted true :updated_at (.getTime (Date.))} comment_id))

(defn- update-comment-score [conn comment_id op]
  (let [i (case op :like 1 :dislike -1)]
    (sql/execute! conn ["UPDATE comments SET score=score + ? WHERE id=?" i comment_id])))

(defn- add-user-bill-comment-ref [conn comment_id user_id op]
  (let [liked (case op :like true :dislike false)]
    (sql/insert! conn "user_comment_scores" {:comment_id comment_id :user_id user_id :liked liked
                                             :created_at (.getTime (Date.)) :updated_at (.getTime (Date.))})))

(defn- delete-user-bill-comment-ref [conn comment_id user_id]
  (sql/delete! conn "user_comment_scores" ["comment_id=? AND user_id=?" comment_id user_id]))

(defn score-bill-comment [comment_id user_id op]
  (sql/with-db-transaction [d db/db]
    (update-comment-score d comment_id op)
    (add-user-bill-comment-ref d comment_id user_id op)))

(defn revoke-liked-bill-comment-score [comment_id user_id]
  (log/info "Removing bill comment score for User " user_id " and Comment " comment_id)
  (sql/with-db-transaction [d db/db]
    (update-comment-score d comment_id :dislike)
    (delete-user-bill-comment-ref d comment_id user_id)))

(defn revoke-disliked-bill-comment-score [comment_id user_id]
  (log/info "Removing bill comment score for User " user_id " and Comment " comment_id)
  (sql/with-db-transaction [d db/db]
    (update-comment-score d comment_id :like)
    (delete-user-bill-comment-ref d comment_id user_id)))

(defn get-user-bill-comment-score [comment_id user_id]
  (first (sql/query db/db ["SELECT * FROM user_comment_scores where user_id=? AND comment_id=?" user_id comment_id])))