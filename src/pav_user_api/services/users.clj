(ns pav-user-api.services.users
  (:require [environ.core :refer [env]]
            [buddy.hashers :as h]
            [pav-user-api.schema.user :refer [validate validate-facebook validate-login validate-facebook-login construct-error-msg]]
            [pav-user-api.entities.user :as user-dao]
            [pav-user-api.neo4j.users :as neo-dao]
            [buddy.sign.jws :as jws]
            [buddy.sign.util :as u]
            [buddy.core.keys :as ks]
            [clj-time.core :as t]
            [clojure.tools.logging :as log]))

(defn- pkey []
  (ks/private-key (:auth-priv-key env) (:auth-priv-key-pwd env)))

(defn create-auth-token [user]
  {:token (jws/sign user (pkey)
                    {:alg :rs256
                     :exp (-> (t/plus (t/now) (t/days 30)) (u/to-timestamp))})})

(defn associate-token-with-user [user token]
  (merge user token))

(defn create-facebook-user [user]
  (log/info (str "Creating user " user " from facebook"))
  (let [pav-token (create-auth-token (dissoc user :token))]
    (try
      (user-dao/create-facebook-user-with-token user pav-token)
      (neo-dao/create-user user)
      {:record (assoc user :token pav-token)}
      (catch Exception e (log/error e)))))

(defn create-user [user]
  (log/info (str "Creating user " (dissoc user :password)))
  (let [hashed-user (update-in user [:password] #(h/encrypt %))
        token (create-auth-token (dissoc hashed-user :password))]
    (try
     (user-dao/create-user-with-token hashed-user token)
     (neo-dao/create-user hashed-user)
     {:record (dissoc (associate-token-with-user hashed-user token) :password)}
     (catch Exception e (log/info e)))))

(defn update-user-token [user origin]
  (let [new-token (create-auth-token (dissoc user :password))]
    (case origin
      :pav (user-dao/update-user-token user new-token)
      :facebook (user-dao/update-facebook-user-token user new-token))
    (user-dao/update-user-token user new-token)))

(defn get-users []
  (map #(dissoc % :password :id) (user-dao/get-all-users)))

(defn get-user [email]
  (let [user (first (user-dao/get-user email))]
    (if user
      (dissoc user :password :id))))

(defn validate-user-payload [user]
  (let [result (validate user)]
    (if-not (nil? result)
      {:errors (construct-error-msg result)})))

(defn validate-facebook-user-payload [user]
  (let [result (validate-facebook user)]
    (if-not (nil? result)
      {:errors (construct-error-msg result)})))

(defn validate-user-login [user origin]
  (let [result (validate-login user origin)]
    (if-not (nil? result)
      {:errors (construct-error-msg result)})))

(defn user-exist? [user]
  (if (empty? (get-user (get-in user [:email])))
    false
    true))

(defn check-pwd [user existing-user]
  (h/check (:password user) (:password existing-user)))

(defn valid-user? [user origin]
  (case origin
    :pav (check-pwd user (user-dao/get-user-credientials (get-in user [:email])))
    :facebook (user-exist? user)))

(defn authenticate-user [user origin]
  {:record (dissoc (->> (update-user-token user origin)
                        (associate-token-with-user user)) :password :email)})

(defn is-authenticated? [user]
  (if-not (nil? user)
    true
    false))