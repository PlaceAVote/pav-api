(ns com.pav.user.api.resources.user
 (:require [liberator.core :refer [resource defresource]]
           [liberator.representation :refer [ring-response]]
           [com.pav.user.api.services.users :as service]
           [com.pav.user.api.utils.utils :refer [record-in-ctx retrieve-body
																								 retrieve-body-param retrieve-user-details
																								 retrieve-token-user-id retrieve-request-param]]
           [cheshire.core :as ch]))

(def existing-user-error-msg {:error "A User already exists with this email"})
(def login-error-msg "Invalid Login credientials")

(defn retrieve-page-param [payload]
 (let [page (get-in payload [:request :params :page])]
  (if page
   (read-string page)
   nil)))

(defresource create
 :allowed-methods [:put]
 :available-media-types ["application/json"]
 :malformed? (fn [ctx] (service/validate-user-payload (retrieve-body ctx) :pav))
 :conflict? (fn [ctx] (service/user-exist? (retrieve-body ctx)))
 :put! (fn [ctx] (service/create-user-profile (retrieve-body ctx)))
 :handle-created :record
 :handle-conflict existing-user-error-msg
 :handle-malformed (fn [ctx] (ch/generate-string (get-in ctx [:errors]))))

(defresource create-facebook
 :allowed-methods [:put]
 :available-media-types ["application/json"]
 :malformed? (fn [ctx] (service/validate-user-payload (retrieve-body ctx) :facebook))
 :conflict? (fn [ctx] (service/user-exist? (retrieve-body ctx)))
 :put! (fn [ctx] (service/create-user-profile (retrieve-body ctx) :facebook))
 :handle-created :record
 :handle-conflict existing-user-error-msg
 :handle-malformed (fn [ctx] (ch/generate-string (get-in ctx [:errors]))))

(defresource authenticate [origin]
 :allowed-methods [:post]
 :malformed? (fn [ctx] (service/validate-user-login (retrieve-body ctx) origin))
 :authorized? (fn [ctx] (service/valid-user? (retrieve-body ctx) origin))
 :available-media-types ["application/json"]
 :post! (fn [ctx] (service/authenticate-user (retrieve-body ctx) origin))
 :handle-created :record
 :handle-unauthorized login-error-msg
 :handle-malformed (fn [ctx] (ch/generate-string (get-in ctx [:errors]))))

(defresource user
 :authorized? (fn [ctx] (service/is-authenticated? (retrieve-user-details ctx)))
 :allowed-methods [:delete]
 :available-media-types ["application/json"]
 :delete! (fn [ctx] (service/delete-user (retrieve-user-details ctx)))
 :handle-ok record-in-ctx)

(defresource user-settings
	:authorized? (fn [ctx] (service/is-authenticated? (retrieve-user-details ctx)))
	:malformed? (fn [ctx] (service/validate-settings-update (retrieve-body ctx)))
	:allowed-methods [:post :get]
	:available-media-types ["application/json"]
	:post! (fn [ctx] (service/update-account-settings (retrieve-token-user-id ctx) (retrieve-body ctx)))
	:handle-malformed (fn [ctx] (ch/generate-string (get-in ctx [:errors])))
	:handle-ok (fn [ctx] (service/get-account-settings (retrieve-token-user-id ctx))))

(defresource change-password
	:authorized? (fn [ctx] (and (service/is-authenticated? (retrieve-user-details ctx))
													    (service/password-matches?
																(retrieve-token-user-id ctx)
																(retrieve-body-param ctx :current_password))))
	:malformed? (fn [ctx] (service/validate-password-change (retrieve-body ctx)))
	:allowed-methods [:post :get]
	:available-media-types ["application/json"]
	:post! (fn [ctx] (service/change-password (retrieve-token-user-id ctx) (retrieve-body-param ctx :new_password))))

(defresource user-profile
 :authorized? (fn [ctx] (service/is-authenticated? (retrieve-user-details ctx)))
 :allowed-methods [:get]
 :available-media-types ["application/json"]
 :exists? (fn [ctx]
           (if-not (nil? (retrieve-request-param ctx :user_id))
						 {:record (service/get-user-profile (retrieve-token-user-id ctx) (retrieve-request-param ctx :user_id))}
						 {:record (service/get-user-profile (retrieve-token-user-id ctx))}))
 :handle-ok record-in-ctx)

(defresource confirm-user [token]
 :authorized? (fn [_] (service/confirm-token-valid? token))
 :allowed-methods [:post]
 :available-media-types ["application/json"]
 :post! (service/update-registration token))

(defresource notifications
 :authorized? (fn [ctx] (service/is-authenticated? (retrieve-user-details ctx)))
 :allowed-methods [:get]
 :available-media-types ["application/json"]
 :handle-ok (fn [ctx] (service/get-notifications (retrieve-token-user-id ctx))))

(defresource mark-notification [id]
	:authorized? (fn [ctx] (service/is-authenticated? (retrieve-user-details ctx)))
	:allowed-methods [:post]
	:available-media-types ["application/json"]
	:post! (service/mark-notification id))

(defresource reset-password [email]
	:authorized? (service/user-exist? {:email email})
	:allowed-methods [:post]
	:available-media-types ["application/json"]
	:post! (service/issue-password-reset-request email))

(defresource confirm-password-reset
	:allowed-methods [:post]
	:available-media-types ["application/json"]
	:post! (fn [ctx] (let [{token :reset_token password :new_password} (retrieve-body ctx)]
										 (service/confirm-password-reset token password))))

(defresource timeline
 :authorized? (fn [ctx] (service/is-authenticated? (retrieve-user-details ctx)))
 :allowed-methods [:get]
 :available-media-types ["application/json"]
 :handle-ok (fn [ctx]
             (if-not (nil? (retrieve-request-param ctx :user_id))
              (service/get-timeline (retrieve-request-param ctx :user_id) (retrieve-page-param ctx))
              (service/get-timeline (retrieve-token-user-id ctx) (retrieve-page-param ctx)))))

(defresource feed
 :authorized? (fn [ctx] (service/is-authenticated? (retrieve-user-details ctx)))
 :allowed-methods [:get]
 :available-media-types ["application/json"]
 :handle-ok (fn [ctx] (service/get-feed (retrieve-token-user-id ctx))))

(defresource follow
 :authorized? (fn [ctx] (service/is-authenticated? (retrieve-user-details ctx)))
 :allowed-methods [:put]
 :available-media-types ["application/json"]
 :put! (fn [ctx] (service/follow-user (retrieve-token-user-id ctx) (retrieve-body-param ctx :user_id))))

(defresource unfollow
 :authorized? (fn [ctx] (service/is-authenticated? (retrieve-user-details ctx)))
 :allowed-methods [:delete]
 :available-media-types ["application/json"]
 :delete! (fn [ctx] (service/unfollow-user (retrieve-token-user-id ctx) (retrieve-body-param ctx :user_id))))

(defresource following
 :authorized? (fn [ctx] (service/is-authenticated? (retrieve-user-details ctx)))
 :allowed-methods [:get]
 :available-media-types ["application/json"]
 :handle-ok (fn [ctx]
             (if-not (nil? (retrieve-request-param ctx :user_id))
              (service/user-following (retrieve-request-param ctx :user_id))
              (service/user-following (retrieve-token-user-id ctx)))))

(defresource followers
 :authorized? (fn [ctx] (service/is-authenticated? (retrieve-user-details ctx)))
 :allowed-methods [:get]
 :available-media-types ["application/json"]
 :handle-ok (fn [ctx]
             (if-not (nil? (retrieve-request-param ctx :user_id))
              (service/user-followers (retrieve-request-param ctx :user_id))
              (service/user-followers (retrieve-token-user-id ctx)))))

(defresource validate-token
 :authorized? (fn [ctx] (service/validate-token (retrieve-request-param ctx :token)))
 :allowed-methods [:get]
 :available-media-types ["application/json"]
 :handle-ok {:message "Token is valid"})