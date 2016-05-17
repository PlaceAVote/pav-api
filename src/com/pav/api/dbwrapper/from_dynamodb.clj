(ns com.pav.api.dbwrapper.from-dynamodb
  "Utility functions to move data from DynamoDB to SQL database."
  (:require [com.pav.api.dynamodb.user :as du]
            [com.pav.api.db.user :as su]
            [com.pav.api.dbwrapper.user :refer [convert-user-profile]]
            [com.pav.api.dynamodb.comments :as dc]
            [com.pav.api.db.comment :as sc]
            [com.pav.api.dbwrapper.comment :refer [dynamodb->sql-comment]]
            [clojure.tools.logging :as log]))

(defn- migrate-user
  "Copy single user from dynamodb to sql table."
  [user]
  (let [email (:email user)]
    (if-let [found (su/get-user-by-email email)]
      (log/infof "Skipping user '%s' (found under id: %s)" email (:user_id found))
      (try
        (log/infof "Migrating user '%s'" email)
        (-> user convert-user-profile su/create-user)
        (catch Throwable e
          (log/errorf "Failed for '%s' with %s" email (.getMessage e)))))))

(defn- migrate-bill-comment
  "Copy single bill comment from dynamodb to sql table"
  [comment]
  (if-let [{comment_id :comment_id} (sc/get-bill-comment-by-old-id (:comment_id comment))]
    (log/infof (log/infof "Skipping comment found using old id '%s' " comment_id))
    (try
      (log/infof "Migrating comment %s" comment_id)
      (-> comment dynamodb->sql-comment sc/create-bill-comment)
      (catch Throwable e
        (log/errorf "Failed for comment %s" comment_id (.getMessage e))))))

(defn- migrate-users
  "Copy all dynamodb users to sql user table."
  []
  (let [users (du/retrieve-all-user-records)]
    (doseq [u users]
      (migrate-user u))))

(defn- migrate-bill-comments
  "Copy all dynamodb bill comments to sql user_bill_comments and comments table"
  []
  (let [comments (dc/retrieve-all-bill-comments)]
    (doseq [c comments]
      (migrate-bill-comment c))))

(defn migrate-all-data
  "Migrate all data to SQL database."
  []
  (migrate-users))
