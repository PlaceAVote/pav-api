(ns com.pav.user.api.resources.user
 (:require [liberator.core :refer [resource defresource]]
           [liberator.representation :refer [ring-response]]
           [com.pav.user.api.services.users :as service]
           [com.pav.user.api.utils.utils :refer [record-in-ctx retrieve-body retrieve-user-details
                                                 retrieve-user-email retrieve-user-id]]
           [cheshire.core :as ch]))

(def existing-user-error-msg {:error "A User already exists with this email"})
(def login-error-msg "Invalid Login credientials")

(defresource create [payload]
 :allowed-methods [:put]
 :available-media-types ["application/json"]
 :malformed? (fn [_] (service/validate-user-payload (retrieve-body payload) :pav))
 :conflict? (fn [_] (service/user-exist? (retrieve-body payload)))
 :put! (fn [_] (service/create-user (retrieve-body payload)))
 :handle-created :record
 :handle-conflict existing-user-error-msg
 :handle-malformed (fn [ctx] (ch/generate-string (get-in ctx [:errors]))))

(defresource create-facebook [payload]
 :allowed-methods [:put]
 :available-media-types ["application/json"]
 :malformed? (fn [_] (service/validate-user-payload (retrieve-body payload) :facebook))
 :conflict? (fn [_] (service/user-exist? (retrieve-body payload)))
 :put! (fn [_] (service/create-facebook-user (retrieve-body payload)))
 :handle-created :record
 :handle-conflict existing-user-error-msg
 :handle-malformed (fn [ctx] (ch/generate-string (get-in ctx [:errors]))))

(defresource authenticate [payload origin]
 :allowed-methods [:post]
 :malformed? (fn [_] (service/validate-user-login (retrieve-body payload) origin))
 :authorized? (fn [_] (service/valid-user? (retrieve-body payload) origin))
 :available-media-types ["application/json"]
 :post! (fn [_] (service/authenticate-user (retrieve-body payload) origin))
 :handle-created :record
 :handle-unauthorized login-error-msg
 :handle-malformed (fn [ctx] (ch/generate-string (get-in ctx [:errors]))))

(defresource user
 :authorized? (fn [ctx] (service/is-authenticated? (retrieve-user-details (:request ctx))))
 :allowed-methods [:get]
 :available-media-types ["application/json"]
 :exists? (fn [ctx]
           (if-not (nil? (get-in ctx [:request :params :user_id]))
            {:record (service/get-user-by-id (get-in ctx [:request :params :user_id]))}
            {:record (service/get-user-by-id (retrieve-user-id (:request ctx)))}))
 :handle-ok record-in-ctx)

(defresource confirm-user [token]
 :authorized? (fn [_] (service/confirm-token-valid? token))
 :allowed-methods [:post]
 :available-media-types ["application/json"]
 :post! (service/update-registration token))

(defresource notifications [email]
 :authorized? (fn [ctx] (service/is-authenticated? (retrieve-user-details (:request ctx))))
 :allowed-methods [:get]
 :available-media-types ["application/json"]
 :handle-ok (fn [ctx] (service/get-notifications (retrieve-user-id (:request ctx)))))

(defresource timeline
 :authorized? (fn [ctx] (service/is-authenticated? (retrieve-user-details (:request ctx))))
 :allowed-methods [:get]
 :available-media-types ["application/json"]
 :handle-ok (fn [ctx] (service/get-timeline (retrieve-user-id (:request ctx)))))