(ns com.pav.user.api.authentication.authentication
  (:require [buddy.auth.backends.token :refer [jws-backend]]
            [buddy.core.keys :as ks]
            [clojure.tools.logging :as log]))

(defn token-handler [env]
  (jws-backend
    {:secret  (ks/public-key (:auth-pub-key env))
     :options {:alg :rs256}
     :token-name "PAV_AUTH_TOKEN"
     :on-error   (fn [req e]
                   (log/errorf "Exception Failed decrypting token %s REQUEST %s" e req))}))
