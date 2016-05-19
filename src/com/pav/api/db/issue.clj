(ns com.pav.api.db.issue
  (:require [com.pav.api.db.db :as db]
            [com.pav.api.db.tables :refer [user-issues-table user-issue-responses-table]]
            [clojure.java.jdbc :as sql]
            [clojure.tools.logging :as log]
            [com.pav.api.utils.utils :as u]
            [com.pav.api.db.common :refer [extract-value unclobify]]))


(defn create-user-issue [issue]
  (when-let [id (extract-value (sql/insert! db/db user-issues-table issue))]
    (log/info "Persisted new user issue " issue)
    id))

(defn update-user-issue [user_id issue_id updates]
  (log/infof "Updating user issue for user '%s' and issue '%s'" user_id issue_id)
  (sql/update! db/db user-issues-table updates [(u/sstr "id=? and user_id=?") issue_id user_id]))

(defn mark-user-issue-for-deletion [user_id issue_id]
  (log/infof "Marking user issue for deletion for user '%s' and issue '%s'" user_id issue_id)
  (update-user-issue user_id issue_id {:deleted true}))

(defn get-user-issue-by-old-id [old_issue_id]
  (first (sql/query db/db
           [(u/sstr "SELECT * FROM " user-issues-table " WHERE old_issue_id=?") old_issue_id]
           {:row-fn unclobify})))


(defn get-user-issue-response [user_id issue_id]
  (first (sql/query db/db
           [(u/sstr "SELECT * FROM " user-issue-responses-table " WHERE issue_id=? AND user_id=?") issue_id user_id]
           {:row-fn unclobify})))

(defn get-user-issue-response-by-old-ids [old_issue_id old_user_id]
  (first (sql/query db/db
           [(u/sstr "SELECT * FROM " user-issue-responses-table " WHERE old_issue_id=? AND old_user_id=?")
            old_issue_id old_user_id] {:row-fn unclobify})))

(defn update-user-issue-response [id props]
  (log/infof "Updating user issue response for '%s'" id)
  (sql/update! db/db user-issue-responses-table props [(u/sstr "id=?") id]))

(defn create-user-issue-response [issue-res]
  (log/infof "Creating user issue response for issue '%s' and user '%s'" (:issue_id issue-res) (:user_id issue-res))
  (extract-value (sql/insert! db/db user-issue-responses-table issue-res)))

(defn upsert-user-issue-emotional-response [{u :user_id i :issue_id :as issue-res}]
  (if-let [found (get-user-issue-response u i)]
    (update-user-issue-response (:id found) (select-keys issue-res [:emotional_response]))
    (create-user-issue-response issue-res)))

(defn delete-user-issue-emotional-response [issue_id user_id]
  (log/infof "Deleting user issue response for issue '%s' and user '%s'" issue_id user_id)
  (sql/delete! db/db user-issue-responses-table [(u/sstr "issue_id = ? AND user_id=?") issue_id user_id]))