(ns pav-user-api.database.database
  (:use korma.db)
  (:import (com.mysql.jdbc.jdbc2.optional MysqlDataSource))
  (:require [environ.core :refer [env]]))

(defdb user_db (mysql
                {:db (:mysql-database env)
                 :user (:mysql-user env)
                 :password (:mysql-password env)
                 :host (:mysql-host env)
                 :port (:mysql-port env)}))

(defn get-db-datasource []
  (doto (MysqlDataSource.)
    (.setUrl (str "jdbc:mysql://" (:mysql-host env) ":" (:mysql-port env) "/" (:mysql-database env)))
    (.setUser (:mysql-user env))
    (.setPassword (:mysql-password env))))
