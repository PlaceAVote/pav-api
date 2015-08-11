(ns pav-user-api.services.users
  (:require [clojurewerkz.neocons.rest.nodes :as nn]
            [clojurewerkz.neocons.rest :as nr]
            [clojurewerkz.neocons.rest.labels :as nl]
            [clojurewerkz.neocons.rest.cypher :as cy]
            [cheshire.core :as ch]
            [environ.core :refer [env]]))

(def connection (nr/connect (:neo-url env) (:neo-username env) (:neo-password env)))

(defn create-user [user]
  (nl/add connection (nn/create connection user) "User"))

(defn get-users []
  (ch/generate-string (cy/tquery connection "MATCH (u:User) RETURN u.email AS email, u.password AS password")))