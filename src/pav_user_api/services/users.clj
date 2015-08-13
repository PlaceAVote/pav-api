(ns pav-user-api.services.users
  (:require [clojurewerkz.neocons.rest.nodes :as nn]
            [clojurewerkz.neocons.rest :as nr]
            [clojurewerkz.neocons.rest.labels :as nl]
            [clojurewerkz.neocons.rest.cypher :as cy]
            [environ.core :refer [env]]
            [buddy.hashers :as h]
            [pav-user-api.schema.user :refer [validate construct-error-msg]]))

(def connection (nr/connect (:neo-url env) (:neo-username env) (:neo-password env)))

(defn create-user [user]
  (let [hashed-user (update-in user [:password] #(h/encrypt %))]
    (try
     (nl/add connection (nn/create connection hashed-user) "User")
     {:record (dissoc user :password)}
     (catch Exception e (println e)))))

(defn get-users []
  (cy/tquery connection "MATCH (u:User) RETURN u.email AS email"))

(defn get-user [email]
    (first (cy/tquery connection "MATCH (u:User {email: {email}}) RETURN u.email AS email" {:email email})))

(defn bind-any-errors? [user]
  (let [result (validate user)]
    (if-not (nil? result)
      {:errors (construct-error-msg result)})))

(defn user-exist? [user]
  (if (empty? (get-user (get-in user [:email])))
    false
    true))