(ns com.pav.api.dbwrapper.issue
  (:require [com.pav.api.dbwrapper.helpers :refer [with-sql-backend]]
            [com.pav.api.db.user :as sql-u]
            [com.pav.api.db.issue :as sql-i]
            [com.pav.api.utils.utils :refer [prog1]]
            [com.pav.api.dynamodb.user :as du]))

(defn dynamo-issue->sql [issue]
  (->
    (select-keys issue [:bill_id :article_link :article_title :article_img :comment])
    (merge
      {:short_issue_id (:short_issue_id issue)
       :user_id        (:user_id (sql-u/get-user-by-old-id (:user_id issue)))
       :old_user_id    (:user_id issue)
       :old_issue_id   (:issue_id issue)
       :created_at     (:timestamp issue)
       :updated_at     (:timestamp issue)
       :deleted        (or (:deleted issue) false)})))

(defn create-user-issue [issue]
  (prog1
    (du/create-bill-issue issue)
    (with-sql-backend
      (-> issue dynamo-issue->sql sql-i/create-user-issue))))

(defn update-user-issue [user_id issue_id updates]
  (prog1
    (du/update-user-issue user_id issue_id updates)
    (with-sql-backend
      (sql-i/update-user-issue
        (:user_id (sql-u/get-user-by-old-id user_id))
        (:id (sql-i/get-user-issue-by-old-id issue_id))
        updates))))