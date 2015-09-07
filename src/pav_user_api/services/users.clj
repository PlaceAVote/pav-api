(ns pav-user-api.services.users
  (:require [environ.core :refer [env]]
            [buddy.hashers :as h]
            [pav-user-api.schema.user :refer [validate validate-login construct-error-msg]]
            [pav-user-api.entities.user :as user-dao]
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

(defn create-user [user]
  (log/info (str "Creating user " (dissoc user :password)))
  (let [hashed-user (update-in user [:password] #(h/encrypt %))
        token (create-auth-token (dissoc hashed-user :password))]
    (try
     (user-dao/create-user-with-token hashed-user token)
     {:record (dissoc (associate-token-with-user hashed-user token) :password)}
     (catch Exception e (log/info e)))))

(defn get-users []
  (map #(dissoc % :password :id) (user-dao/get-all-users)))

(defn get-user [email]
  (let [user (first (user-dao/get-user email))]
    (if user
      (dissoc user :password :id))))

(defn get-user-details [email]
  (let [user (user-dao/get-user-credientials email)]
    user))

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
      (if (h/check (:password user) (get-in existing-user [:password]))
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