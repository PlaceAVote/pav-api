(ns com.pav.api.db.db
  "General functions for handling MySQL database, modeled to have
similar API as dynamodb."
  (:require [clojure.java.jdbc :as sql]
            [environ.core :refer [env]]
            [clojure.tools.logging :as log]))

(defn- parse-url
  "Expects url in form 'mysql://address' or 'h2://path' and return
a map with :subprotocol and :subname as appropriate url. Only takes into
account first column; other columns are ignored, so db type for H2 can
be specified.

It also does one more thing for H2 database: sets it to MySQL mode by
appending 'mode=mysql' to url, if not given."
  [url]
  {:post [(and (contains? % :subprotocol)
               (contains? % :subname))]}
  (let [[subproto path] (-> url .toLowerCase (.split ":" 2))
        mysql-mode-str "mode=mysql"
        ret {:subprotocol subproto, :subname path}]
    (if (and (= "h2" subproto)
             (not (.contains path mysql-mode-str)))
      (update-in ret [:subname] #(str % ";" mysql-mode-str))
      ret)))

(def ^{:doc "Database access object."
       :public true}
  db (merge
      (parse-url (:db-url env))
      {:user (:db-user env)
       :password (:db-pwd env)}))

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
             "activity_feed_subscribers"
             "user_activity_feed"
             "activity_event_type"
             "user_info"
             "schema_version" ;; this is flyway metadata
             ]]
    (safe-drop-table t true)))
