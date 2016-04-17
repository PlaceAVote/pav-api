(ns com.pav.api.resources.user
 (:require [liberator.core :refer [resource defresource]]
           [liberator.representation :refer [ring-response]]
           [com.pav.api.services.users :as service]
           [com.pav.api.services.questions :as q-service]
           [com.pav.api.utils.utils :refer [record-in-ctx retrieve-body
																								 retrieve-body-param retrieve-user-details
																								 retrieve-token-user-id retrieve-request-param]]
					 [com.pav.api.utils.utils :refer [decodeBase64ImageString]]
           [cheshire.core :as ch]))

(def existing-user-error-msg {:error "A User already exists with this email"})
(def login-error-msg {:error "Invalid Login credientials"})

(defresource validate-user
  :service-available? {:representation {:media-type "application/json"}}
  :allowed-methods [:post]
  :available-media-types ["application/json"]
  :malformed? (fn [ctx] (if-let [errors (service/validate-user-properties (retrieve-body ctx))]
                          {::errors {:errors errors}}))
  :handle-malformed ::errors
  :handle-created (ring-response {:status 200}))

(defresource create
  :service-available? {:representation {:media-type "application/json"}}
  :allowed-methods [:put]
  :available-media-types ["application/json"]
  :malformed? (fn [ctx] (service/validate-new-user-payload (retrieve-body ctx) :pav))
  :conflict? (fn [ctx] (service/user-exist? (retrieve-body ctx)))
  :put! (fn [ctx] (service/create-user-profile (retrieve-body ctx)))
  :handle-created :record
  :handle-conflict existing-user-error-msg
  :handle-malformed (fn [ctx] (ch/generate-string (get-in ctx [:errors]))))

(defresource create-facebook
  :service-available? {:representation {:media-type "application/json"}}
  :allowed-methods [:put]
  :available-media-types ["application/json"]
  :malformed? (fn [ctx] (service/validate-new-user-payload (retrieve-body ctx) :facebook))
  :conflict? (fn [ctx] (service/user-exist? (retrieve-body ctx)))
  :put! (fn [ctx] (service/create-user-profile (retrieve-body ctx) :facebook))
  :handle-created :record
  :handle-conflict existing-user-error-msg
  :handle-malformed (fn [ctx] (get-in ctx [:errors])))

(defresource authenticate [origin]
  :service-available? {:representation {:media-type "application/json"}}
  :allowed-methods [:post]
  :malformed? (fn [ctx] (service/validate-user-login-payload (retrieve-body ctx) origin))
  :authorized? (fn [ctx] (service/valid-user? (retrieve-body ctx) origin))
  :available-media-types ["application/json"]
  :post! (fn [ctx] (service/authenticate-user (retrieve-body ctx) origin))
  :handle-created :record
  :handle-unauthorized login-error-msg
  :handle-malformed (fn [ctx] (get-in ctx [:errors])))

(defresource user
  :service-available? {:representation {:media-type "application/json"}}
  :authorized? (fn [ctx] (service/is-authenticated? (retrieve-user-details ctx)))
  :allowed-methods [:delete]
  :available-media-types ["application/json"]
  :delete! (fn [ctx] (service/delete-user (retrieve-user-details ctx)))
  :handle-ok record-in-ctx)

(defresource user-settings
  :service-available? {:representation {:media-type "application/json"}}
  :authorized? (fn [ctx] (service/is-authenticated? (retrieve-user-details ctx)))
  :malformed? (fn [ctx] (service/validate-settings-update-payload (retrieve-body ctx)))
  :allowed-methods [:post :get]
  :available-media-types ["application/json"]
  :post! (fn [ctx] (service/update-account-settings (retrieve-token-user-id ctx) (retrieve-body ctx)))
  :handle-malformed (fn [ctx] (get-in ctx [:errors]))
  :handle-ok (fn [ctx] (service/get-account-settings (retrieve-token-user-id ctx))))

(defresource upload-profile-image
  :service-available? {:representation {:media-type "application/json"}}
	:authorized? (fn [ctx] (service/is-authenticated? (retrieve-user-details ctx)))
	:malformed? (fn [ctx] (let [f (decodeBase64ImageString (retrieve-body-param ctx :file))]
													(if (service/valid-image? f) [false {:image f}] true)))
	:allowed-methods [:post]
	:available-media-types ["application/json"]
	:post! (fn [ctx] {:record (service/upload-profile-image (retrieve-token-user-id ctx) (:image ctx))})
 	:handle-created :record)

(defresource change-password
  :service-available? {:representation {:media-type "application/json"}}
  :authorized? (fn [ctx] (and (service/is-authenticated? (retrieve-user-details ctx))
                              (service/password-matches?
                               (retrieve-token-user-id ctx)
                               (retrieve-body-param ctx :current_password))))
  :malformed? (fn [ctx] (service/validate-password-change-payload (retrieve-body ctx)))
  :allowed-methods [:post :get]
  :available-media-types ["application/json"]
  :post! (fn [ctx] (service/change-password (retrieve-token-user-id ctx) (retrieve-body-param ctx :new_password))))

(defresource user-profile
  :service-available? {:representation {:media-type "application/json"}}
  :allowed-methods [:get]
  :available-media-types ["application/json"]
  :exists? (fn [ctx]
             (if-let [id (retrieve-request-param ctx :user_id)]
               (service/user-profile-exist? (retrieve-token-user-id ctx) id)
               (service/user-profile-exist? (retrieve-token-user-id ctx))))
  :handle-ok :record
  :handle-not-found (fn [ctx] (select-keys ctx [:error])))

(defresource confirm-user [token]
  :service-available? {:representation {:media-type "application/json"}}
  :authorized? (fn [_] (service/confirm-token-valid? token))
  :allowed-methods [:post]
  :available-media-types ["application/json"]
  :post! (service/update-registration token)
  :handle-unauthorized {:error "Not Authorized"})

(defresource notifications [from]
  :service-available? {:representation {:media-type "application/json"}}
  :authorized? (fn [ctx] (service/is-authenticated? (retrieve-user-details ctx)))
  :allowed-methods [:get]
  :available-media-types ["application/json"]
  :handle-ok (fn [ctx] (service/get-notifications (retrieve-token-user-id ctx) from)))

(defresource mark-notification [id]
  :service-available? {:representation {:media-type "application/json"}}
  :authorized? (fn [ctx] (service/is-authenticated? (retrieve-user-details ctx)))
  :allowed-methods [:post]
  :available-media-types ["application/json"]
  :post! (service/mark-notification id))

(defresource reset-password [email]
  :service-available? {:representation {:media-type "application/json"}}
  :authorized? (service/allowed-to-reset-password? email)
  :allowed-methods [:post]
  :available-media-types ["application/json"]
  :post! (service/issue-password-reset-request email))

(defresource confirm-password-reset
  :service-available? {:representation {:media-type "application/json"}}
  :authorized? (fn [ctx] (service/valid-reset-token? (:reset_token (retrieve-body ctx))))
  :allowed-methods [:post]
  :available-media-types ["application/json"]
  :malformed? (fn [ctx] (service/validate-password-reset-confirmation-payload (retrieve-body ctx)))
  :handle-malformed (fn [ctx] (get-in ctx [:errors]))
  :post! (fn [ctx] (let [{token :reset_token password :new_password} (retrieve-body ctx)]
                     (service/confirm-password-reset token password))))

(defresource timeline [from]
  :service-available? {:representation {:media-type "application/json"}}
  :authorized? (fn [ctx] (service/is-authenticated? (retrieve-user-details ctx)))
  :allowed-methods [:get]
  :available-media-types ["application/json"]
  :handle-ok (fn [ctx]
               (if-not (nil? (retrieve-request-param ctx :user_id))
                 (service/get-timeline (retrieve-request-param ctx :user_id) from)
                 (service/get-timeline (retrieve-token-user-id ctx) from))))

(defresource feed [from]
  :service-available? {:representation {:media-type "application/json"}}
  :authorized? (fn [ctx] (service/is-authenticated? (retrieve-user-details ctx)))
  :allowed-methods [:get]
  :available-media-types ["application/json"]
  :handle-ok (fn [ctx] (service/get-feed (retrieve-token-user-id ctx) from)))

(defresource questions
  :service-available? {:representation {:media-type "application/json"}}
  :authorized? (fn [ctx] (service/is-authenticated? (retrieve-user-details ctx)))
  :allowed-methods [:get :post]
  :available-media-types ["application/json"]
  :post! (fn [ctx] (q-service/submit-answers (retrieve-token-user-id ctx) (retrieve-body ctx)))
  :handle-ok (fn [ctx] (q-service/retrieve-questions (retrieve-token-user-id ctx))))

(defresource follow
  :service-available? {:representation {:media-type "application/json"}}
  :authorized? (fn [ctx] (service/is-authenticated? (retrieve-user-details ctx)))
  :malformed? (fn [ctx]
                (if (= (retrieve-token-user-id ctx) (retrieve-body-param ctx :user_id))
                  true false))
  :allowed-methods [:put]
  :available-media-types ["application/json"]
  :put! (fn [ctx] (service/follow-user (retrieve-token-user-id ctx) (retrieve-body-param ctx :user_id)))
  :handle-malformed {:error "You cannot follow yourself"})

(defresource unfollow
  :service-available? {:representation {:media-type "application/json"}}
  :authorized? (fn [ctx] (service/is-authenticated? (retrieve-user-details ctx)))
  :allowed-methods [:delete]
  :available-media-types ["application/json"]
  :delete! (fn [ctx] (service/unfollow-user (retrieve-token-user-id ctx) (retrieve-body-param ctx :user_id))))

(defresource following
  :service-available? {:representation {:media-type "application/json"}}
  :authorized? (fn [ctx] (service/is-authenticated? (retrieve-user-details ctx)))
  :allowed-methods [:get]
  :available-media-types ["application/json"]
  :handle-ok (fn [ctx]
               (service/user-following
                (if-let [id (retrieve-request-param ctx :user_id)]
                  id
                  (retrieve-token-user-id ctx)))))

(defresource followers
  :service-available? {:representation {:media-type "application/json"}}
  :authorized? (fn [ctx] (service/is-authenticated? (retrieve-user-details ctx)))
  :allowed-methods [:get]
  :available-media-types ["application/json"]
  :handle-ok (fn [ctx]
               (service/user-followers
                (if-let [id (retrieve-request-param ctx :user_id)]
                  id
                  (retrieve-token-user-id ctx)))))

(defresource validate-token
  :service-available? {:representation {:media-type "application/json"}}
  :authorized? (fn [ctx] (service/validate-token (retrieve-request-param ctx :token)))
  :allowed-methods [:get]
  :available-media-types ["application/json"]
  :handle-ok {:message "Token is valid"})

(defresource create-user-issue
  :service-available? {:representation {:media-type "application/json"}}
  :authorized? (fn [ctx] (service/is-authenticated? (retrieve-user-details ctx)))
  :allowed-methods [:put]
  :available-media-types ["application/json"]
  :malformed? (fn [ctx] (service/validate-newissue-payload (retrieve-body ctx)))
  :handle-malformed (fn [ctx] (ch/generate-string (get-in ctx [:errors])))
  ;; see http://stackoverflow.com/questions/17765718/post-request-with-clojure-liberator
  ;; about using map to capture response and 
  ;; http://stackoverflow.com/questions/23723785/clojure-liberator-returning-json-from-a-put-request
  ;; on for PUT we have to implement 'handle-created'
  :put! (fn [ctx] {::user-issue-response
                   (service/create-bill-issue (retrieve-token-user-id ctx) (retrieve-body ctx))})
  :handle-created ::user-issue-response)

(defresource get-user-issue [issue_id]
  :service-available? {:representation {:media-type "application/json"}}
  :allowed-methods [:get]
  :available-media-types ["application/json"]
  :exists? (fn [ctx] (if-let [issue (service/get-user-issue-feed-item issue_id (retrieve-token-user-id ctx))]
                       [true {:record issue}]
                       [false {:error {:error_message "Issue not found"}}]))
  :handle-ok :record
  :handle-not-found :error)

(defresource update-user-issue [issue_id]
  :service-available? {:representation {:media-type "application/json"}}
  :authorized? (fn [ctx] (and (service/is-authenticated? (retrieve-user-details ctx))
                           (not (nil? (service/get-user-issue (retrieve-token-user-id ctx) issue_id)))))
  :allowed-methods [:post]
  :available-media-types ["application/json"]
  :post! (fn [ctx] {::user-issue-response
                    (service/update-user-issue (retrieve-token-user-id ctx) issue_id (retrieve-body ctx))})
  :handle-created ::user-issue-response)

(defresource contact-form
  :service-available? {:representation {:media-type "application/json"}}
  :allowed-methods [:post]
  :available-media-types ["application/json"]
  :malformed? (fn [ctx] (service/contact-form-email-malformed? (retrieve-body ctx)))
  :post! (fn [ctx] (service/send-contact-form-email (retrieve-body ctx))))

(defresource user-issue-emotional-response [issue_id]
  :service-available? {:representation {:media-type "application/json"}}
  :authorized? (fn [ctx] (service/is-authenticated? (retrieve-user-details ctx)))
  :allowed-methods [:get :post :delete]
  :available-media-types ["application/json"]
  :malformed? (fn [ctx]
                ;; make sure this is checked or will be triggered on GET too
                (when (= :post (-> ctx :request :request-method))
                  (let [body-errors?   (-> ctx
                                        retrieve-body
                                        service/validate-user-issue-emotional-response)
                        issue-exists? (service/user-issue-exist? issue_id)]
                    (if (and (false? body-errors?) issue-exists?)
                      false true))))
  :exists? (service/user-issue-exist? issue_id)
  :post! (fn [ctx]
           {::user-issue-emotional-response
            (service/update-user-issue-emotional-response issue_id
                                                          (retrieve-token-user-id ctx)
                                                          (retrieve-body ctx))})
  :delete! (fn [ctx]
             (service/delete-user-issue-emotional-response issue_id (retrieve-token-user-id ctx)))
  :handle-created ::user-issue-emotional-response
  :handle-ok (fn [ctx]
               (service/get-user-issue-emotional-response issue_id
                                                          (retrieve-token-user-id ctx))))
