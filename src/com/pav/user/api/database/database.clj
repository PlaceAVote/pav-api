(ns com.pav.user.api.database.database
  (:require [environ.core :refer [env]]))

(def db-spec
  {:subprotocol "mysql"
   :subname     (str "//" (:mysql-url env))
   :user        (:mysql-user env)
   :password    (:mysql-pwd env)})