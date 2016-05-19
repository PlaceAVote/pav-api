(ns com.pav.api.db.issue
  (:require [com.pav.api.db.db :as db]
            [com.pav.api.db.tables :refer [user-issues-table]]
            [clojure.java.jdbc :as sql]
            [clojure.tools.logging :as log]
            [com.pav.api.utils.utils :as u]
            [com.pav.api.db.common :refer [extract-value unclobify]]))


(defn create-user-issue [issue]
  (when-let [id (extract-value (sql/insert! db/db user-issues-table issue))]
    (log/info "Persisted new user issue " issue)
    id))

(defn get-user-issue-by-old-id [old_issue_id]
  (first (sql/query db/db
           [(u/sstr "SELECT * FROM " user-issues-table " WHERE old_issue_id=?") old_issue_id]
           {:row-fn unclobify})))