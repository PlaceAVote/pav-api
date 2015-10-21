(ns com.pav.user.api.services.users
  (:require [environ.core :refer [env]]
            [buddy.hashers :as h]
            [com.pav.user.api.schema.user :refer [validate validate-login construct-error-msg]]
            [com.pav.user.api.dynamodb.user :as dynamo-dao]
            [com.pav.user.api.timeline.timeline :as timeline-dao]
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

(defn get-user-timeline [email]
  (timeline-dao/get-timeline email))

(defn create-facebook-user [user]
  (log/info (str "Creating user " user " from facebook"))
  (let [user-with-facebook-token (-> (assoc user :facebook_token (:token user))
                                     (merge (create-auth-token (dissoc user :token)))
                                     (assoc :created_at (.getTime (Date.)))
                                     (merge {:registered false}))]
    (try
      {:record (-> (dynamo-dao/create-user user-with-facebook-token)
                   (dissoc :facebook_token))}
      (catch Exception e (log/error e)))))

(defn create-user [user]
  (log/info (str "Creating user " (dissoc user :password)))
  (let [hashed-user (-> (update-in user [:password] #(h/encrypt %))
                        (assoc :created_at (.getTime (Date.)))
                        (merge (create-auth-token (dissoc user :password)))
                        (merge {:registered false}))]
    (try
      {:record (-> (dynamo-dao/create-user hashed-user)
                   (dissoc :password))}
     (catch Exception e (log/info e)))))

(defn update-user-token [user origin]
  (let [new-token (create-auth-token (dissoc user :password))]
    (case origin
      :pav (dynamo-dao/update-user-token user new-token)
      :facebook (dynamo-dao/update-facebook-user-token user new-token))))

(defn get-user [email]
  (let [user (dynamo-dao/get-user email)]
    (if user
      (dissoc user :password :id))))

(defn validate-user-payload [user origin]
  (let [result (validate user origin)]
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
    :pav (check-pwd user (dynamo-dao/get-user (:email user)))
    :facebook (user-exist? user)))

(defn authenticate-user [user origin]
  {:record (dissoc (->> (update-user-token user origin)
                        (associate-token-with-user user)) :password :email)})

(defn is-authenticated? [user]
  (if-not (nil? user)
    true
    false))

(defn update-registration [token]
  (dynamo-dao/update-registration token))

(defn confirm-token-valid? [token]
  (if-not (nil? (dynamo-dao/get-confirmation-token token))
    true
    false))