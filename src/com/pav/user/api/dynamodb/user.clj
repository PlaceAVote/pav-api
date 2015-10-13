(ns com.pav.user.api.dynamodb.user
  (:require [taoensso.faraday :as far]
            [environ.core :refer [env]]
            [clojure.tools.logging :as log]))

(def client-opts {:access-key (:access-key env)
                  :secret-key (:secret-key env)
                  :endpoint (:dynamo-endpoint env)})

(defn create-user [user-profile]
  (try
    (far/put-item client-opts :users user-profile)
    user-profile
  (catch Exception e (log/info (str "Error occured persisting new user-profile " e)))))

(defn update-user-token [user new-token]
  (try
    (far/update-item client-opts :users {:email (:email user)} {:token [:put new-token]})
    (merge user new-token)
  (catch Exception e (log/info (str "Error occured updating user token " e)))))

(defn update-facebook-user-token [user new-token]
  (try
    (far/update-item client-opts :users {:email (:email user)} {:token [:put new-token]
                                                                :facebook_token [:put (:token user)]})
    (merge user new-token)
    (catch Exception e (log/info (str "Error occured updating user token " e)))))

(defn get-user [email]
  (try
    (far/get-item client-opts :users {:email email})
  (catch Exception e (log/info (str "Error occured retrieving user by email " e)))))
