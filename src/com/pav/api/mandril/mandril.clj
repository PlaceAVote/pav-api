(ns com.pav.api.mandril.mandril
  (:require [clojure.tools.logging :as log]
            [environ.core :refer [env]]
						[cheshire.core :as ch]
						[clj-http.client :as client]))

(def mandril-api-key (:mandril-api-key env))
(def email-mode (:email-mode env))
(def password-reset-template (:mandril-pwd-reset-template env))
(def welcome-email-template (:mandril-welcome-email-template env))
(def comment-reply-template (:mandril-comment-reply-template env))
(def email-confirmation-template (:mandril-email-confirmation-template env))

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
  (-> {:message {:to         [{:email email :type "to"}]
                 :important  true
                 :inline_css true}}
    (merge message)))

(defn build-email-confirmation-body
  [message {:keys [email confirmation-token first_name last_name]}]
  (-> {:message {:to                [{:email email :type "to"}]
                 :important         false
                 :inline_css        true
                 :merge             true
                 :merge_language    "handlebars"
                 :global_merge_vars [{:name "first_name" :content first_name}
                                     {:name "last_name" :content last_name}
                                     {:name "confirm_token" :content confirmation-token}]}}
      (merge message)))

(defn build-comment-reply-body
  [message {:keys [email author_first_name author_last_name
                   author_img_url bill_title body
                   bill_id author]}]
  (-> {:message {:to                [{:email email :type "to"}]
                 :important         false
                 :inline_css        true
                 :merge             true
                 :merge_language    "handlebars"
                 :global_merge_vars [{:name "author_first_name" :content author_first_name}
                                     {:name "author_last_name" :content author_last_name}
                                     {:name "author_img_url" :content author_img_url}
                                     {:name "author_id" :content author}
                                     {:name "bill_title" :content bill_title}
                                     {:name "bill_id" :content bill_id}
                                     {:name "body" :content body}]}}
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
  (try
    (when (= email-mode "live")
      (client/post "https://mandrillapp.com/api/1.0/messages/send-template.json" {:body body}))
      (log/info "Email Body being sent to mandril Template " body)
    (catch Exception e (log/error (str "Error occured sending Email to Mandril Template Endpoint " body) e))))

(defn- send-email [body]
  (try
    (when (= email-mode "live")
      (client/post "https://mandrillapp.com/api/1.0/messages/send.json" {:body body}))
    (catch Exception e (log/error (str "Error occured sending Email to Mandril" body) e))))

(defn send-password-reset-email [user token]
  (-> (build-email-header mandril-api-key password-reset-template)
      (build-pwd-reset-body user token)
      ch/generate-string
      send-template-email))

(defn send-welcome-email [user]
  (-> (build-email-header mandril-api-key welcome-email-template)
      (build-welcome-email-body user)
      ch/generate-string
      send-template-email))

(defn send-email-confirmation-email [user]
  (-> (build-email-header mandril-api-key email-confirmation-template)
      (build-email-confirmation-body user)
      ch/generate-string
      send-template-email))

(defn send-comment-reply-email [comment]
  (-> (build-email-header mandril-api-key comment-reply-template)
      (build-comment-reply-body comment)
      ch/generate-string
      send-template-email))

(defn send-contact-form-email [msg-body]
  (-> (build-email-header mandril-api-key)
      (build-contact-form-body msg-body)
      ch/generate-string
      send-email))
