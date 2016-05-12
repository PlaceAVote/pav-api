(ns com.pav.api.dbwrapper.comment
  (:require [com.pav.api.dynamodb.comments :as dynamo]
            [com.pav.api.db.comment :as sql]
            [com.pav.api.db.user :as u-sql]
            [com.pav.api.dbwrapper.helpers :refer [with-sql-backend]]
            [com.pav.api.utils.utils :refer [prog1]]))

(defn- dynamodb->sql-comment [comment]
  (let [c (assoc comment
            :old_comment_id (:comment_id comment)
            :old_user_id (:author comment)
            :user_id (:user_id (u-sql/get-user-by-old-id (:author comment)))
            :created_at (:timestamp comment)
            :updated_at (:timestamp comment))]
    (if-let [p (:parent_id c)]
      (assoc c :parent_id (:id (sql/get-bill-comment-by-old-id p)) :old_parent_id p)
      c)))

(defn create-bill-comment [comment]
  (prog1
    (dynamo/create-bill-comment comment)
    (with-sql-backend
      (sql/create-bill-comment (-> comment dynamodb->sql-comment)))))

(defn update-bill-comment [comment_id props]
  (prog1
    (dynamo/update-bill-comment props comment_id)
    (with-sql-backend
      (sql/update-bill-comment props (:id (sql/get-bill-comment-by-old-id comment_id))))))

(defn mark-bill-comment-for-deletion [comment_id user_id]
  (prog1
    (dynamo/delete-comment comment_id user_id)
    (with-sql-backend
      (sql/mark-bill-comment-for-deletion (:id (sql/get-bill-comment-by-old-id comment_id))))))