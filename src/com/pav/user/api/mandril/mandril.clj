(ns com.pav.user.api.mandril.mandril
  (:require [clojure.tools.logging :as log]
            [environ.core :refer [env]]
						[cheshire.core :as ch]
						[clj-http.client :as client]))

(def mandril-api-key (:mandril-api-key env))
(def email-mode (:email-mode env))
(def password-reset-template (:mandril-pwd-reset-template env))
(def welcome-email-template (:mandril-welcome-email-template env))

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

(defn build-welcome-email-body
  [message {:keys [email]}]
  (-> {:message {:to                [{:email email :type "to"}]
                 :important         true
                 :inline_css        true}}
    (merge message)))

(defn build-contact-form-body [message {:keys [email name body]}]
  (-> {:message {:to         [{:email "hello@placeavote.com" :name "Placeavote" :type "to"}]
                 :headers    {:Reply-To email}
                 :subject    "Contact"
                 :from_email email
                 :from_name  name
                 :text body}}
    (merge message)))

(defn- send-template-email [body]
  (when (= email-mode "live")
    (client/post "https://mandrillapp.com/api/1.0/messages/send-template.json" {:body body})))

(defn- send-email [body]
  (when (= email-mode "live")
    (client/post "https://mandrillapp.com/api/1.0/messages/send.json" {:body body})))

(defn send-password-reset-email [user token]
	(let [body (-> (build-email-header mandril-api-key password-reset-template)
							 	 (build-pwd-reset-body user token)
							 	 ch/generate-string)]
		(log/info "Email Body being sent to mandril " body)
		(try
			(send-template-email body)
			(catch Exception e (log/error "Error sending email " e)))))

(defn send-welcome-email [user]
  (let [body (-> (build-email-header mandril-api-key welcome-email-template)
                 (build-welcome-email-body user)
                 ch/generate-string)]
    (log/info "Email Body being sent to mandril " body)
    (try
      (send-template-email body)
      (catch Exception e (log/error (str "Error sending welcome email to user: " (:user_id user)) e)))))

(defn send-contact-form-email [msg-body]
  (log/info "Sending Email from Contact form for " msg-body)
  (try
    (when-let [body (-> (build-email-header mandril-api-key)
                        (build-contact-form-body msg-body)
                        ch/generate-string)]
      (send-email body))
    (catch Exception e (log/error (str "Error occured sending following contact form email " msg-body) e))))
