(ns com.pav.user.api.migrations.migrations
  (import [org.flywaydb.core Flyway])
  (:require [clojure.java.jdbc :refer [db-do-commands]]
            [environ.core :refer [env]]
            [clojure.tools.logging :as log]
            [com.pav.user.api.dynamodb.db :as dynamo]
            [com.pav.user.api.database.user :refer [create-user]]
            [taoensso.faraday :as far]))

(defn migrate []
  (log/info "Running Flyway Migration Task")
  (let [flyway (doto (Flyway.)
                 (.setDataSource (str "jdbc:mysql://" (:mysql-url env)) (:mysql-user env) (:mysql-pwd env) nil)
                 (.setSqlMigrationPrefix ""))]
    (.migrate flyway)))

(defn repair []
  (log/info "Running Flyway Repair Task")
  (let [flyway (doto (Flyway.)
                 (.setDataSource (str "jdbc:mysql://" (:mysql-url env)) (:mysql-user env) (:mysql-pwd env) nil)
                 (.setSqlMigrationPrefix ""))]
    (.repair flyway)))

(defn migrate-users []
  (log/info "Running User Migration Task")
  (loop [users (far/scan dynamo/client-opts dynamo/user-table-name)]
    (doseq [u users]
      (create-user u))
    (if (:last-prim-kvs (meta users))
      (recur (far/scan dynamo/client-opts dynamo/user-table-name
               {:last-prim-kvs (:last-prim-kvs (meta users))})))))
