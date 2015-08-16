(ns pav-user-api.services.users
  (:require [clojurewerkz.neocons.rest.nodes :as nn]
            [clojurewerkz.neocons.rest :as nr]
            [clojurewerkz.neocons.rest.labels :as nl]
            [clojurewerkz.neocons.rest.cypher :as cy]
            [environ.core :refer [env]]
            [buddy.hashers :as h]
            [pav-user-api.schema.user :refer [validate construct-error-msg]]
            [taoensso.carmine :as car :refer (wcar)]
            [buddy.sign.jws :as jws]
            [buddy.sign.util :as u]
            [buddy.core.keys :as ks]
            [clj-time.core :as t]))

(def red-conn {:pool {} :spec {:host (str (:redis-port-6379-tcp-addr env)) :port (read-string (:redis-port-6379-tcp-port env))}})
(def connection (nr/connect (str "http://" (:neo4j-port-7474-tcp-addr env) ":" (:neo4j-port-7474-tcp-port env) "/db/data") (:neo-username env) (:neo-password env)))

(defn- pkey []
  (ks/private-key (:auth-priv-key env) (:auth-priv-key-pwd env)))

(defn create-auth-token [user]
  {:token (jws/sign user (pkey)
                    {:alg :rs256
                     :exp (-> (t/plus (t/now) (t/days 30)) (u/to-timestamp))})})

(defn associate-token-with-user [user token]
  (car/wcar red-conn (car/set (get-in user [:email]) (get-in token [:token])))
  (merge user token))

(defn create-user [user]
  (let [hashed-user (update-in user [:password] #(h/encrypt %))
        token (create-auth-token hashed-user)]
    (try
     (nl/add connection (nn/create connection hashed-user) "User")
     {:record (dissoc (associate-token-with-user hashed-user token) :password)}
     (catch Exception e (println e)))))

(defn get-users []
  (cy/tquery connection "MATCH (u:User) RETURN u.email AS email"))

(defn get-user [email]
    (first (cy/tquery connection "MATCH (u:User {email: {email}}) RETURN u.email AS email" {:email email})))

(defn get-user-details [email]
  (first (cy/tquery connection "MATCH (u:User {email: {email}}) RETURN u.email AS email, u.password AS password" {:email email})))

(defn bind-any-errors? [user]
  (let [result (validate user)]
    (if-not (nil? result)
      {:errors (construct-error-msg result)})))

(defn user-exist? [user]
  (if (empty? (get-user (get-in user [:email])))
    false
    true))

(defn valid-user? [user]
  (let [existing-user (get-user-details (get-in user [:email]))]
    (if-not (nil? existing-user)
      (if (h/check (:password user) (get-in existing-user ["password"]))
        true
        false)
      false)))

(defn authenticate-user [user]
  {:record (dissoc (->> (create-auth-token user)
                        (associate-token-with-user user)) :password :email)})

(defn is-authenticated? [token]
  (if-not (nil? token)
    true
    false))