(ns com.pav.api.db.db
  "General functions for handling MySQL database, modeled to have
similar API as dynamodb."
  (:require [clojure.java.jdbc :as sql]
            [environ.core :refer [env]]
            [clojure.tools.logging :as log]))

(def ^{:doc "Database access object."
       :public true}
  db {:subprotocol "mysql"
      :subname (:mysql-url env)
      :user (:mysql-user env)
      :password (:mysql-pwd env)})

(defn- table-exists?
  "Returns nil if given table does not exists."
  [table]
  (-> db (sql/query ["SHOW TABLES LIKE ?" table]) seq))

(defn- safe-create-table
  "Create table, but only if not created before. If table exists,
just skipp it."
  [table details]
  (when-not (table-exists? table)
    (log/debugf "Creating table '%s'" table)
    (sql/db-do-commands db
      ;; :table-spec is for table specific details, but is left empty so
      ;; admin can setup the same rules applied on all tables
      (sql/create-table-ddl table details {:table-spec ""}))))

(defn- safe-drop-table
  "Drop table, handling necessary exception(s)."
  ([table throw-on-error?]
     (try
       (sql/db-do-commands db
         (sql/drop-table-ddl table))
       (catch java.sql.SQLException e
         (let [msg (.getMessage e)]
           (if (.startsWith msg "Unknown table")
             (log/debugf "Failed to delete table '%s'. Probably table does not exist, so no worry" table)
             ;; something goes bad, just record it so we can remove other tables
             (do
               (log/errorf e "Unable to properly remove table '%s'" table)
               (when throw-on-error?
                 (throw e))))))))
  ([table] (safe-drop-table table false)))

(defn empty-table-unsafe!
  "Remove everything from the table using TRUNCATE.
Works fast, but can yield malformed constraints."
  [table]
  (sql/db-do-commands db true
                      ["SET FOREIGN_KEY_CHECKS = 0"
                       (str "TRUNCATE TABLE " table)
                       "SET FOREIGN_KEY_CHECKS = 1"]))

(defn drop-all-tables!
  "For database cleanup. Mainly so it can be used from tests or REPL handling."
  []
  ;; Make sure tables are deleted in proper order, to obey foreign key constraints. If
  ;; safe-drop-table was set to ignore this exception, some tables would be deleted and some
  ;; not, so throw this so dev knows something is wrong.
  (doseq [t ["user_following_rel"
             "user_creds_fb"
             "user_creds_pav"
             "user_topic"
             "user_votes"
             "user_issue_responses"
             "user_issue_comments"
             "user_bill_comments"
             "user_comment_scores"
             "user_issues"
             "comments"
             "topic"
             "user_info"
             "schema_version" ;; this is flyway metadata
             ]]
    (safe-drop-table t true)))
