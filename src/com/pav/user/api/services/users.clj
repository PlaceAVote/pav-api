(ns com.pav.user.api.services.users
  (:require [environ.core :refer [env]]
            [buddy.hashers :as h]
            [com.pav.user.api.schema.user :refer [validate validate-login construct-error-msg]]
            [com.pav.user.api.dynamodb.user :as dynamo-dao]
            [com.pav.user.api.redis.redis :as redis-dao]
            [buddy.sign.jws :as jws]
            [buddy.sign.util :as u]
            [buddy.core.keys :as ks]
            [clj-time.core :as t]
            [clojure.tools.logging :as log])
  (:import [java.util Date UUID]))

(defn- pkey []
  (ks/private-key (:auth-priv-key env) (:auth-priv-key-pwd env)))

(defn create-auth-token [user]
  {:token (jws/sign user (pkey)
                    {:alg :rs256
                     :exp (-> (t/plus (t/now) (t/days 30)) (u/to-timestamp))})})

(defn assoc-new-token [user]
  (let [safe-profile (dissoc user :token)
        new-token (create-auth-token safe-profile)]
    (merge safe-profile new-token)))

(defn assoc-common-attributes [user]
  (-> user
      (assoc :user_id (.toString (UUID/randomUUID)))
      (assoc :created_at (.getTime (Date.)))
      (merge {:registered false :public false})))

(defn create-facebook-user [user]
  (log/info (str "Creating user " user " from facebook"))
  (let [user-with-token (-> (assoc-common-attributes user)
                            (assoc :facebook_token (:token user))
                            assoc-new-token)]
    (try
      {:record (-> (dynamo-dao/create-user user-with-token)
                   (dissoc :facebook_token))}
      (catch Exception e (log/error e)))))

(defn create-user [user]
  (log/info (str "Creating user " user))
  (let [user-with-token (-> (update-in user [:password] #(h/encrypt %))
                            assoc-common-attributes
                            assoc-new-token)]
    (try
      {:record (-> (dynamo-dao/create-user user-with-token)
                   (dissoc :password))}
     (catch Exception e (log/info e)))))

(defn remove-sensitive-information [user]
  (if user
    (dissoc user :password)))

(defn get-user-by-id [id]
  (-> (dynamo-dao/get-user-by-id id)
      remove-sensitive-information))

(defn get-user-by-email [email]
  (-> (dynamo-dao/get-user-by-email email)
      remove-sensitive-information))

(defn update-user-token [user origin]
  (let [current-user (get-user-by-email (:email user))
        new-token (create-auth-token (dissoc current-user :password :token))]
    (case origin
      :pav (dynamo-dao/update-user-token user new-token)
      :facebook (dynamo-dao/update-facebook-user-token user new-token))))

(defn validate-user-payload [user origin]
  (let [result (validate user origin)]
    (if-not (nil? result)
      {:errors (construct-error-msg result)})))

(defn validate-user-login [user origin]
  (let [result (validate-login user origin)]
    (if-not (nil? result)
      {:errors (construct-error-msg result)})))

(defn user-exist? [user]
  (if (empty? (get-user-by-email (get-in user [:email])))
    false
    true))

(defn check-pwd [user existing-user]
  (h/check (:password user) (:password existing-user)))

(defn valid-user? [user origin]
  (case origin
    :pav (check-pwd user (dynamo-dao/get-user-by-email (:email user)))
    :facebook (user-exist? user)))

(defn authenticate-user [user origin]
  {:record (-> (update-user-token user origin)
               (select-keys [:token]))})

(defn is-authenticated? [user]
  (if-not (nil? (:user_id user))
    true
    false))

(defn update-registration [token]
  (dynamo-dao/update-registration token))

(defn confirm-token-valid? [token]
  (if-not (nil? (dynamo-dao/get-confirmation-token token))
    true
    false))

(defn get-notifications [user]
  (dynamo-dao/get-notifications user))

(defn get-timeline [user]
  (let [timeline (redis-dao/get-user-timeline user)]
    (if (empty? timeline)
      (dynamo-dao/get-user-timeline user)
      timeline)))