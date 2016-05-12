(ns com.pav.api.db.comment
  (:require [com.pav.api.db.db :as db]
            [clojure.java.jdbc :as sql]
            [clojure.tools.logging :as log]
            [com.pav.api.utils.utils :as u]
            [com.pav.api.db.common :refer [extract-value unclobify]]))

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