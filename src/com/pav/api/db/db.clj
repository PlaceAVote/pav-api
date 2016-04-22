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

(defmacro id-type
  "Returns currently type used for managing application IDs.
This is only helper for easier table schema specification."
  ([o additional]
     (if additional
       `[~o :bigint :unsigned ~additional]
       `[~o :bigint :unsigned]))
  ([o] `(id-type ~o nil)))

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

(defn empty-table!
  "Remove everything from the table."
  [table]
  (sql/db-do-commands db ["TRUNCATE TABLE ?" table]))

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
             "user_info"]]
    (safe-drop-table t true)))

(defn create-all-tables!
  "Create all necessary tables, with schema."
  []
  (log/debug "Creating all database tables...")
  (safe-create-table "user_info"
                     [(id-type :user_id "NOT NULL AUTO_INCREMENT PRIMARY KEY")
                      ;; email length is up to 254 chars, according to RFC5321
                      [:email "varchar(255)" "NOT NULL UNIQUE"]
                      [:first_name "varchar(255)"]
                      [:last_name "varchar(255)"]
                      [:img_url :text]
                      [:gender "varchar(6)"]
                      [:dob :int]
                      [:address :text]
                      [:zipcode :text]
                      [:state :text]
                      ;; for lat/long we are going to use DECIMAL to skip rounding imposed by FLOAT/DOUBLE.
                      ;; See: http://stackoverflow.com/questions/12504208/what-mysql-data-type-should-be-used-for-latitude-longitude-with-8-decimal-place
                      [:latitude "decimal(10, 8)"]
                      [:longtitude "decimal(11, 8)"]
                      [:public_profile :bool]
                      [:created_at :int]
                      [:updated_at :int]
                      [:country_code :text]])

  (safe-create-table "user_creds_fb"
                     ;; about fb id length: http://stackoverflow.com/questions/7566672/whats-the-max-length-of-a-facebook-uid
                     [[:facebook_id "varchar(128)" "NOT NULL" "PRIMARY KEY"]
                      (id-type :user_id)
                      ["FOREIGN KEY(user_id) REFERENCES user_info(user_id) ON DELETE CASCADE"]])

  (safe-create-table "user_creds_pav"
                     [[:id :int "NOT NULL AUTO_INCREMENT PRIMARY KEY"]
                      (id-type :user_id)
                      [:password :text]
                      ["FOREIGN KEY(user_id) REFERENCES user_info(user_id) ON DELETE CASCADE"]])

  (safe-create-table "topic"
                     [[:id :int "NOT NULL AUTO_INCREMENT PRIMARY KEY"]
                      [:name :text]])

  (safe-create-table "user_topic"
                     [[:id :int "NOT NULL AUTO_INCREMENT PRIMARY KEY"]
                      (id-type :user_id)
                      [:topic_id :int]
                      ["FOREIGN KEY(user_id) REFERENCES user_info(user_id) ON DELETE CASCADE"]
                      ["FOREIGN KEY(topic_id) REFERENCES topic(id) ON DELETE SET NULL"]])

  (safe-create-table "user_following_rel"
                     [[:id :int "NOT NULL AUTO_INCREMENT PRIMARY KEY"]
                      (id-type :user_id)
                      (id-type :following_id)
                      ["FOREIGN KEY(user_id) REFERENCES user_info(user_id) ON DELETE CASCADE"]
                      ["FOREIGN KEY(following_id) REFERENCES user_info(user_id) ON DELETE CASCADE"]])

  (safe-create-table "user_votes"
                     [[:id :int "NOT NULL AUTO_INCREMENT PRIMARY KEY"]
                      (id-type :user_id)
                      [:bill_id "varchar(10)"]
                      [:vote :bool]
                      [:created_at :int]
                      ["FOREIGN KEY(user_id) REFERENCES user_info(user_id) ON DELETE CASCADE"]])

  (safe-create-table "user_issues"
                     [[:id :int "NOT NULL AUTO_INCREMENT PRIMARY KEY"]
                      [:short_issue_id "varchar(32)" "UNIQUE"]
                      (id-type :user_id)
                      [:updated_at :int]
                      [:created_at :int]
                      [:comment_body :text]
                      [:negative_responses :int :unsigned]
                      [:neutral_responses :int :unsigned]
                      [:positive_responses :int :unsigned]
                      [:bill_id "varchar(10)"]
                      [:article_link :text]
                      [:article_title :text]
                      [:article_img :text]
                      ["FOREIGN KEY(user_id) REFERENCES user_info(user_id) ON DELETE CASCADE"]])

  (safe-create-table "user_issue_comments"
                     [[:id :int "NOT NULL AUTO_INCREMENT PRIMARY KEY"]
                      [:issue_id :int]
                      ["FOREIGN KEY(issue_id) REFERENCES user_issues(id) ON DELETE CASCADE"]])

  (safe-create-table "user_issue_responses"
                     [[:id :int "NOT NULL AUTO_INCREMENT PRIMARY KEY"]
                      (id-type :user_id)
                      [:issue_id :int]
                      [:response :int]
                      ["FOREIGN KEY(user_id) REFERENCES user_info(user_id) ON DELETE CASCADE"]
                      ["FOREIGN KEY(issue_id) REFERENCES user_issues(id) ON DELETE CASCADE"]])

  (safe-create-table "comments"
                     [[:id :int "NOT NULL AUTO_INCREMENT PRIMARY KEY"]
                      (id-type :user_id)
                      ;; FIXME: no :parent_id here, since it points to nowhere
                      [:body :text]
                      [:has_children :bool]
                      [:score :int]
                      [:created_at :int]
                      [:updated_at :int]
                      [:deleted :bool]
                      ["FOREIGN KEY(user_id) REFERENCES user_info(user_id) ON DELETE CASCADE"]])

  (safe-create-table "user_bill_comments"
                     [[:id :int "NOT NULL AUTO_INCREMENT PRIMARY KEY"]
                      [:comment_id :int]
                      [:bill_id "varchar(10)"]
                      ["FOREIGN KEY(comment_id) REFERENCES comments(id) ON DELETE CASCADE"]])

  (safe-create-table "user_comment_scores"
                     [[:id :int "NOT NULL AUTO_INCREMENT PRIMARY KEY"]
                      (id-type :user_id)
                      [:comment_id :int]
                      [:liked :int] ;; FIXME: only liked or liked/disliked??
                      [:created_at :int]
                      [:updated_at :int]
                      [:bill_id "varchar(10)"]
                      ["FOREIGN KEY(user_id) REFERENCES user_info(user_id) ON DELETE CASCADE"]
                      ["FOREIGN KEY(comment_id) REFERENCES comments(id) ON DELETE CASCADE"]])

)
