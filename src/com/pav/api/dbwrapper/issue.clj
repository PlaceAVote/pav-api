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

(defn dynamo-issueres->sql-issueres [issue-res]
  {:user_id            (:user_id (sql-u/get-user-by-old-id (:user_id issue-res)))
   :issue_id           (:id (sql-i/get-user-issue-by-old-id (:issue_id issue-res)))
   :old_user_id        (:user_id issue-res)
   :old_issue_id       (:issue_id issue-res)
   :emotional_response (case (:emotional_response issue-res)
                         "positive" 1
                         "neutral" 0
                         "negative" -1
                         0)})

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

(defn mark-user-issue-for-deletion [user_id issue_id]
  (prog1
    (du/mark-user-issue-for-deletion user_id issue_id)
    (with-sql-backend
      (sql-i/mark-user-issue-for-deletion
        (:user_id (sql-u/get-user-by-old-id user_id))
        (:id (sql-i/get-user-issue-by-old-id issue_id))))))

(defn update-user-issue-emotional-response [issue_id user_id resp]
  (let [dynamo-ret (du/update-user-issue-emotional-response issue_id user_id resp)]
    (with-sql-backend
      (-> dynamo-ret dynamo-issueres->sql-issueres sql-i/upsert-user-issue-emotional-response))
    dynamo-ret))

(defn delete-user-issue-emotional-response [issue_id user_id]
  (prog1
    (du/delete-user-issue-emotional-response issue_id user_id)
    (with-sql-backend
      (sql-i/delete-user-issue-emotional-response
        (:id (sql-i/get-user-issue-by-old-id issue_id))
        (:user_id (sql-u/get-user-by-old-id user_id))))))