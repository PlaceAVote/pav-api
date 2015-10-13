(ns com.pav.user.api.dynamodb.user
  (:require [taoensso.faraday :as far]
            [environ.core :refer [env]]
            [clojure.tools.logging :as log]))

(def client-opts {:access-key (:access-key env)
                  :secret-key (:secret-key env)
                  :endpoint (:dynamo-endpoint env)})

(def user-table-name (:dynamo-user-table-name env))

(defn create-user [user-profile]
  (try
    (far/put-item client-opts user-table-name user-profile)
    user-profile
  (catch Exception e (log/info (str "Error occured persisting new user-profile " e)))))

(defn update-user-token [user new-token]
  (try
    (far/update-item client-opts user-table-name {:email (:email user)} {:token [:put new-token]})
    (merge user new-token)
  (catch Exception e (log/info (str "Error occured updating user token " e)))))

(defn update-facebook-user-token [user new-token]
  (try
    (far/update-item client-opts user-table-name {:email (:email user)} {:token [:put new-token]
                                                                :facebook_token [:put (:token user)]})
    (merge user new-token)
    (catch Exception e (log/info (str "Error occured updating user token " e)))))

(defn get-user [email]
  (try
    (far/get-item client-opts user-table-name {:email email})
  (catch Exception e (log/info (str "Error occured retrieving user by email " e)))))
