(ns com.pav.user.api.dynamodb.user
  (:require [taoensso.faraday :as far]
            [environ.core :refer [env]]
            [clojure.tools.logging :as log]
            [clojurewerkz.mailer.core :refer [delivery-mode!
                                              build-email
                                              deliver-email
                                              with-settings
                                              with-delivery-mode]])
  (:import (java.util UUID)))

(def client-opts {:access-key (:access-key env)
                  :secret-key (:secret-key env)
                  :endpoint (:dynamo-endpoint env)})

(def user-table-name (:dynamo-user-table-name env))
(def user-confirm-table-name (:dynamo-user-confirmation-table-name env))
(def email-host (:email-host env))
(def email-user (:email-user env))
(def email-pass (:email-pass env))
(def email-delivery-mode (keyword (:email-mode env)))
(def email-port (:email-port env))

(defn send-confirmation-email [user confirmation-token]
  (try
    (with-settings
      {:host email-host :user email-user :pass email-pass :port (read-string email-port) :ssl true}
     (with-delivery-mode email-delivery-mode (deliver-email {:from "PlaceAVote Support Team" :to [(:email user)] :subject "Confirm Email Address"}
                                                            "templates/confirm_email.html" {:first_name            (:first_name user)
                                                                                            :last_name             (:last_name user)
                                                                                            :confirm-email-address (str "www.placeavote.com/user/confirm/" confirmation-token)}
                                                            :text/html)))
  (catch Exception e (log/error (str "Error sending confirmation email to " user " " e)))))

(defn associate-confirmation-token-with-user [email token]
  (far/put-item client-opts user-confirm-table-name {:confirmation-token token
                                                     :email email}))

(defn create-user [user-profile]
  (let [confirmation-token (.toString (UUID/randomUUID))]
    (try
     (far/put-item client-opts user-table-name user-profile)
     (associate-confirmation-token-with-user (:email user-profile) confirmation-token)
     (send-confirmation-email user-profile confirmation-token)
     user-profile
     (catch Exception e (log/info (str "Error occured persisting new user-profile " e " to table " confirmation-token))))))

(defn update-user-token [user new-token]
  (try
    (far/update-item client-opts user-table-name {:email (:email user)} {:token [:put (:token new-token)]})
    (merge user new-token)
  (catch Exception e (log/info (str "Error occured updating user token " e)))))

(defn update-facebook-user-token [user new-token]
  (try
    (far/update-item client-opts user-table-name {:email (:email user)} {:token          [:put (:token new-token)]
                                                                         :facebook_token [:put (:token user)]})
    (merge user new-token)
    (catch Exception e (log/info (str "Error occured updating user token " e)))))

(defn get-user [email]
  (try
    (far/get-item client-opts user-table-name {:email email})
  (catch Exception e (log/info (str "Error occured retrieving user by email " e)))))

(defn get-confirmation-token [token]
  (far/get-item client-opts user-confirm-table-name {:confirmation-token token}))

(defn update-registration [token]
  (try
    (let [{email :email} (get-confirmation-token token)]
      (if-not (empty? email)
        (far/update-item client-opts user-table-name {:email email} {:registered [:put true]})))
  (catch Exception e (log/info (str "Error occured updating registeration status for token " token " " e)))))