(ns pav-user-api.services.users
  (:require [clojurewerkz.neocons.rest.nodes :as nn]
            [clojurewerkz.neocons.rest :as nr]
            [clojurewerkz.neocons.rest.labels :as nl]
            [clojurewerkz.neocons.rest.cypher :as cy]
            [cheshire.core :as ch]
            [environ.core :refer [env]]
            [pav-user-api.schema.user :refer [validate construct-error-msg]]))

(def connection (nr/connect (:neo-url env) (:neo-username env) (:neo-password env)))

(defn create-user [user]
  (try
    (nl/add connection (nn/create connection user) "User")
    {:record user}
  (catch Exception e (println e))))

(defn get-users []
  (cy/tquery connection "MATCH (u:User) RETURN u.email AS email, u.password AS password"))

(defn get-user [email]
    (first (cy/tquery connection "MATCH (u:User {email: {email}}) RETURN u.email AS email, u.password AS password" {:email email})))

(defn bind-any-errors? [user]
  (let [result (validate user)]
    (if-not (nil? result)
      {:errors (construct-error-msg result)})))

(defn user-exist? [user]
  (if (empty? (get-user (get-in user [:email])))
    false
    true))