(ns com.pav.api.db.db
  "General functions for handling MySQL database, modeled to have
similar API as dynamodb."
  (:require [com.pav.api.db.tables :as table]
            [clojure.java.jdbc :as sql]
            [environ.core :refer [env]]
            [clojure.tools.logging :as log]
            [com.pav.api.utils.utils :refer [set-env!]]))

(defn parse-url
  "Expects url in form 'mysql://address' or 'h2://path' and return
a map with :subprotocol and :subname as appropriate url. Only takes into
account first column; other columns are ignored, so db type for H2 can
be specified.

It also does couple of one more thing for H2 database: sets it to MySQL mode by
appending 'mode=mysql' to url, if not given and, when H2 memory mode is used, sets
db_close_delay=-1 to keep memory reference live as long as JVM is alive."
  [url]
  {:post [(and (contains? % :subprotocol)
               (contains? % :subname))]}
  (let [[subproto path] (-> url .toLowerCase (.split ":" 2))
        ;; for keeping H2 in mysql mode
        mysql-mode-str "mode=mysql"
        ;; In case of memory mode, make sure database is alive as long as JVM is alive.
        ;; Otherwise, it will be recreated for every new call, which will make migrations unusable.
        h2-mem-persistent "db_close_delay=-1"
        addons (if (= "h2" subproto)
                 (str
                  (if-not (.contains path mysql-mode-str)
                    (str ";" mysql-mode-str))
                  (if (and (.contains path "mem:")
                           (not (.contains path h2-mem-persistent)))
                    (str ";" h2-mem-persistent))))
        ret {:subprotocol subproto, :subname path}]
    (if addons
      (update-in ret [:subname] #(str % addons))
      ret)))

(defn- parse-url-fallback
  "Call parse-url, but in case of nil value, return hardcoded H2 memory database."
  [url default]
  (parse-url (if url
               url
               (do (log/warn "Failed to load database url. Using hardocoded value:" default)
                   default))))

(def ^{:doc "Database access object."
       :public true}
  db (merge
      (parse-url-fallback (:db-url env) "h2:mem:pav")
      {:user (:db-user env)
       :password (:db-pwd env)}))

(defn- table-exists?
  "Returns nil if given table does not exists."
  [table]
  (if (= "mysql" (:subprotocol db))
    (-> db (sql/query ["SHOW TABLES LIKE ?" table]) seq)
    (try
      ;; fallback, since H2 does not understaind above construct
      (sql/query db [(str "SELECT 1 FROM " table)])
      (catch java.sql.SQLException _
        nil))))

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

(defn empty-all-tables-unsafe!
  "Empty all tables using 'SHOW TABLES' and 'empty-table-unsafe!'."
  []
  (log/infof "Cleaning ALL tables in %s database" (:subprotocol db))
  (doseq [t (sql/query db ["SHOW TABLES"])]
    ;; ignore errors to skip some visible but not deleteable tables,
    ;; like schema_version, on H2
    (try
      (-> t first val empty-table-unsafe!)
      (catch Exception _))))

(defn drop-all-tables!
  "For database cleanup. Mainly so it can be used from tests or REPL handling."
  []
  ;; Make sure tables are deleted in proper order, to obey foreign key constraints. If
  ;; safe-drop-table was set to ignore this exception, some tables would be deleted and some
  ;; not, so throw this so dev knows something is wrong.
  (doseq [t [table/user-followers-table
             table/user-creds-fb-table
             table/user-confirmation-tokens-table
             table/user-creds-pav-table
             table/user-topics-table
             table/user-votes-table
             table/user-issue-responses-table
             table/user-issue-comments-table
             table/user-bill-comments-table
             table/user-comment-scores-table
             table/user-issues-table
             table/comments-table
             table/topics-table
             table/activity-feed-subscribers-table
             table/user-activity-feeds-table
             table/activity-event-types-table
             table/user-info-table
             "schema_version" ;; this is flyway metadata
             ]]
    (safe-drop-table t true)))

(defn set-db-url!
  "Set database url to the new value. Use this only for runtime changes, because
it will reload 'com.pav.api.db.db' namespace."
  [url]
  (set-env! :db-url url)
  (require '[com.pav.api.db.db] :reload))
