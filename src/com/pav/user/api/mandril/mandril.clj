(ns com.pav.user.api.mandril.mandril
  (:require [clojurewerkz.mailer.core :refer [delivery-mode!
                                              build-email
                                              deliver-email
                                              with-settings
                                              with-delivery-mode]]
            [clojure.tools.logging :as log]
            [environ.core :refer [env]]
						[cheshire.core :as ch]
						[clj-http.client :as client]))

(def host (:email-host env))
(def from (:email-user env))
(def password (:email-pass env))
(def mode (keyword (:email-mode env)))
(def port (:email-port env))
(def mandril-api-key (:mandril-api-key env))
(def password-reset-template (:mandril-pwd-reset-template env))


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

(defn build-email-header
  ([api-key template]
   {:key api-key :template_name template :template_content [] :async true})
  ([api-key]
    {:key api-key :async true}))

(defn build-pwd-reset-body
	[message {:keys [email first_name last_name]} token]
	(-> {:message {:to                [{:email email :type "to"}]
								 :important         false
								 :inline_css        true
								 :merge             true
								 :merge_language    "handlebars"
								 :global_merge_vars [{:name "first_name" :content first_name}
																		 {:name "last_name" :content last_name}
																		 {:name "reset_token" :content token}]}}
		(merge message)))

(defn build-contact-form-body [message {:keys [email name body]}]
  (-> {:message {:to         [{:email "hello@placeavote.com" :name "Placeavote" :type "to"}]
                 :headers    {:Reply-To email}
                 :subject    "Contact"
                 :from_email email
                 :from_name  name
                 :text body}}
    (merge message)))

(defn send-password-reset-email [user token]
	(let [body (-> (build-email-header mandril-api-key password-reset-template)
							 	 (build-pwd-reset-body user token)
							 	 ch/generate-string)]
		(log/info "Email Body being sent to mandril " body)
		(try
			(client/post "https://mandrillapp.com/api/1.0/messages/send-template.json" {:body body})
			(catch Exception e (log/error "Error sending email " e)))))

(defn send-contact-form-email [msg-body]
  (log/info "Sending Email from Contact form for " msg-body)
  (try
    (when-let [body (-> (build-email-header mandril-api-key)
                        (build-contact-form-body msg-body)
                        ch/generate-string)]
      (client/post "https://mandrillapp.com/api/1.0/messages/send.json" {:body body}))
    (catch Exception e (log/error (str "Error occured sending following contact form email " msg-body) e))))
