(ns pav-user-api.migrations.migrations
  (import [org.flywaydb.core Flyway]
          (com.mysql.jdbc.jdbc2.optional MysqlDataSource))
  (:require [clojure.java.jdbc :refer [db-do-commands]]))

(defn get-db-datasource []
  (doto (MysqlDataSource.)
        (.setUrl "jdbc:mysql://localhost:3306/pav_user")
        (.setUser "root")
        (.setPassword "root")))

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