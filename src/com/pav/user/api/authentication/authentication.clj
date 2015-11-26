(ns com.pav.user.api.authentication.authentication
  (:require [buddy.auth.backends.token :refer [jws-backend]]
            [buddy.core.keys :as ks]
            [buddy.sign.jws :as jws]
            [clj-time.core :as t]
            [buddy.sign.util :as u]
            [clojure.tools.logging :as log]
            [environ.core :refer [env]]))

(defn token-handler [env]
  (jws-backend
    {:secret  (ks/public-key (:auth-pub-key env))
     :options {:alg :rs256}
     :token-name "PAV_AUTH_TOKEN"
     :on-error   (fn [req _]
                   (log/error (str "Token not present or is malformed " req)))}))

(defn token-valid? [token]
  (try
    (-> (jws/unsign token (ks/public-key (:auth-pub-key env)) {:alg :rs256})
        (contains? :user_id))
  (catch Exception e
    (log/warn token e)
    false)))

(defn- pkey []
  (ks/private-key (:auth-priv-key env) (:auth-priv-key-pwd env)))

(defn create-auth-token [user]
  {:token (jws/sign user (pkey)
                    {:alg :rs256
                     :exp (-> (t/plus (t/now) (t/days 30)) (u/to-timestamp))})})