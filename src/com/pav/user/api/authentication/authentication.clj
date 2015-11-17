(ns com.pav.user.api.authentication.authentication
  (:require [buddy.auth.backends.token :refer [jws-backend]]
            [buddy.core.keys :as ks]
            [buddy.sign.jws :as jws]
            [clojure.tools.logging :as log]
            [environ.core :refer [env]]))

(defn token-handler [env]
  (jws-backend
    {:secret  (ks/public-key (:auth-pub-key env))
     :options {:alg :rs256}
     :token-name "PAV_AUTH_TOKEN"
     :on-error   (fn [req e]
                   (log/error (str "Exception Failed decrypting token " e " REQUEST " req)))}))

(defn token-valid? [token]
  (try
    (-> (jws/unsign token (ks/public-key (:auth-pub-key env)) {:alg :rs256})
        (contains? :user_id))
  (catch Exception e
    (log/warn token e)
    false)))