(ns com.pav.user.api.mandril.mandril
  (:require [clojurewerkz.mailer.core :refer [delivery-mode!
                                              build-email
                                              deliver-email
                                              with-settings
                                              with-delivery-mode]]
            [clojure.tools.logging :as log]
            [environ.core :refer [env]]))

(def host (:email-host env))
(def from (:email-user env))
(def password (:email-pass env))
(def mode (keyword (:email-mode env)))
(def port (:email-port env))

(defn send-email [first_name last_name confirmation-token email]
  (try
    (with-settings
      {:host host :user from :pass password :port (read-string port) :tls true}
      (with-delivery-mode mode (deliver-email
                                 {:from from
                                  :to [email]
                                  :subject "Confirm Email Address"}
                                  "templates/confirm_email.html" {:first_name            first_name
                                                                  :last_name             last_name
                                                                  :confirm-email-address (str "www.placeavote.com/user/confirm/" confirmation-token)}
                                 :text/html)))
  (catch Exception e (log/error (str "Error sending confirmation email to " email " " e)))))

(defn send-confirmation-email [{first_name :first_name last_name :last_name
                                email :email confirmation_token :confirmation-token}]
  (send-email first_name last_name confirmation_token email))
