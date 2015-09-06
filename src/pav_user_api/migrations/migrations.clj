(ns pav-user-api.migrations.migrations
  (import [org.flywaydb.core Flyway])
  (:require [clojure.java.jdbc :refer [db-do-commands]]
            [pav-user-api.database.database :refer [get-db-datasource]]))

(defn migrate []
  (let [datasource (get-db-datasource)
        flyway (doto (Flyway.)
                 (.setDataSource datasource)
                 (.setSqlMigrationPrefix ""))]
    (.migrate flyway)))

(defn repair []
  (let [datasource (get-db-datasource)
        flyway (doto (Flyway.)
                 (.setDataSource datasource)
                 (.setSqlMigrationPrefix ""))]
    (.repair flyway)))