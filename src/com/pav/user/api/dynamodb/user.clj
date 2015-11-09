(ns com.pav.user.api.dynamodb.user
  (:require [taoensso.faraday :as far]
            [environ.core :refer [env]]
            [clojure.tools.logging :as log]))

(def client-opts {:access-key (:access-key env)
                  :secret-key (:secret-key env)
                  :endpoint (:dynamo-endpoint env)})

(def user-table-name (:dynamo-user-table-name env))
(def user-confirm-table-name (:dynamo-user-confirmation-table-name env))
(def notification-table-name (:dynamo-notification-table-name env))
(def timeline-table-name (:dynamo-usertimeline-table-name env))

(defn get-user-by-id [id]
  (try
    (far/get-item client-opts user-table-name {:user_id id})
    (catch Exception e (log/info (str "Error occured retrieving user by id " e)))))

(defn get-user-by-email [email]
  (try
    (first (far/query client-opts user-table-name {:email [:eq email]} {:index "user-email-idx"}))
    (catch Exception e (log/info (str "Error occured retrieving user by email " e)))))

(defn create-user [user-profile]
  (try
   (far/put-item client-opts user-table-name user-profile)
   user-profile
   (catch Exception e (log/info (str "Error occured persisting new user-profile " e " to table " user-table-name)))))

(defn update-user-token [user new-token]
  (let [{user_id :user_id} (get-user-by-email (:email user))]
    (try
     (far/update-item client-opts user-table-name {:user_id user_id} {:token [:put (:token new-token)]})
     (merge user new-token)
    (catch Exception e (log/info (str "Error occured updating user token " e))))))

(defn update-facebook-user-token [user new-token]
  (let [{user_id :user_id} (get-user-by-email (:email user))]
    (try
     (far/update-item client-opts user-table-name {:user_id user_id} {:token          [:put (:token new-token)]
                                                                      :facebook_token [:put (:token user)]})
     (merge user new-token)
    (catch Exception e (log/info (str "Error occured updating user token " e))))))

(defn get-confirmation-token [token]
  (far/get-item client-opts user-confirm-table-name {:confirmation-token token}))

(defn update-registration [token]
  (try
    (let [{user_id :user_id} (get-confirmation-token token)]
      (if-not (empty? user_id)
        (far/update-item client-opts user-table-name {:user_id user_id} {:registered [:put true]})))
  (catch Exception e (log/info (str "Error occured updating registeration status for token " token " " e)))))

(defn get-notifications [user_id]
  (far/query client-opts notification-table-name {:user_id [:eq user_id]}
                                                 {:order :desc}))

(defn get-user-timeline [user_id]
  (far/query client-opts timeline-table-name {:user_id [:eq user_id]}
             {:order :desc}))