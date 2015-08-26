(ns pav-user-api.services.users
  (:require [clojurewerkz.neocons.rest.nodes :as nn]
            [clojurewerkz.neocons.rest :as nr]
            [clojurewerkz.neocons.rest.labels :as nl]
            [clojurewerkz.neocons.rest.cypher :as cy]
            [environ.core :refer [env]]
            [buddy.hashers :as h]
            [pav-user-api.schema.user :refer [validate validate-login construct-error-msg]]
            [taoensso.carmine :as car :refer (wcar)]
            [buddy.sign.jws :as jws]
            [buddy.sign.util :as u]
            [buddy.core.keys :as ks]
            [clj-time.core :as t]
            [clojure.tools.logging :as log]))

(def red-conn {:pool {} :spec {:host (str (:redis-port-6379-tcp-addr env)) :port (read-string (:redis-port-6379-tcp-port env))}})
(defmacro wcar* [& body] `(car/wcar red-conn ~@body))
(def connection (nr/connect (str "http://" (:neo4j-port-7474-tcp-addr env) ":" (:neo4j-port-7474-tcp-port env) "/db/data") (:neo-username env) (:neo-password env)))

(defn- pkey []
  (ks/private-key (:auth-priv-key env) (:auth-priv-key-pwd env)))

(defn create-auth-token [user]
  {:token (jws/sign user (pkey)
                    {:alg :rs256
                     :exp (-> (t/plus (t/now) (t/days 30)) (u/to-timestamp))})})

(defn associate-token-with-user [user token]
  (try
    (wcar* (car/set (get-in user [:email]) (get-in token [:token])))
  (catch Exception e (log/info (str "Exception writing to Redis at " red-conn e))))
  (merge user token))

(defn create-user [user]
  (log/info (str "Creating user " (dissoc user :password)))
  (let [hashed-user (update-in user [:password] #(h/encrypt %))
        token (create-auth-token (dissoc hashed-user :password))]
    (try
     (nl/add connection (nn/create connection hashed-user) "User")
     {:record (dissoc (associate-token-with-user hashed-user token) :password)}
     (catch Exception e (log/info e)))))

(defn get-users []
  (cy/tquery connection "MATCH (u:User) RETURN u.email AS email, u.first_name AS first_name, u.last_name AS last_name, u.dob AS dob, u.country_code AS country_code"))

(defn get-user [email]
    (first (cy/tquery connection "MATCH (u:User {email: {email}}) RETURN u.email AS email, u.first_name AS first_name, u.last_name AS last_name, u.dob AS dob, u.country_code AS country_code" {:email email})))

(defn get-user-details [email]
  (first (cy/tquery connection "MATCH (u:User {email: {email}}) RETURN u.email AS email, u.password AS password" {:email email})))

(defn bind-any-errors? [user]
  (let [result (validate user)]
    (if-not (nil? result)
      {:errors (construct-error-msg result)})))

(defn validate-user-login [user]
  (let [result (validate-login user)]
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

(defn is-authenticated? [user]
  (if-not (nil? user)
    true
    false))