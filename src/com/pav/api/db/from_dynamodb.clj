(ns com.pav.api.db.from-dynamodb
  "Utility functions to move data from DynamoDB to SQL database."
  (:require [com.pav.api.dynamodb.user :as du]
            [com.pav.api.db.user :as su]
            [com.pav.api.dbwrapper.user :refer [convert-user-profile]]
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

(defn- migrate-users
  "Copy all dynamodb users to sql user table."
  []
  (let [users (du/retrieve-all-user-records)]
    (doseq [u users]
      (migrate-user u))))

(defn migrate-all-data
  "Migrate all data to SQL database."
  []
  (migrate-users))
