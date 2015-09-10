(ns pav-user-api.resources.user
 (:require [liberator.core :refer [resource defresource]]
           [liberator.representation :refer [ring-response]]
           [pav-user-api.services.users :as service]
           [pav-user-api.utils.utils :refer [record-in-ctx retrieve-body retrieve-user-details]]
           [cheshire.core :as ch]
           [clojure.tools.logging :as log]))

(def existing-user-error-msg {:error "A User already exists with this email"})
(def login-error-msg "Invalid Login credientials")

(defresource list-users [payload]
 :authorized? (fn [ctx] (service/is-authenticated? (retrieve-user-details payload)))
 :available-media-types ["application/json"]
 :handle-ok (service/get-users))

(defresource create [payload]
 :allowed-methods [:put]
 :available-media-types ["application/json"]
 :malformed? (fn [ctx] (service/bind-any-errors? (retrieve-body payload)))
 :conflict? (fn [ctx] (service/user-exist? (retrieve-body payload)))
 :put! (fn [ctx] (service/create-user (retrieve-body payload)))
 :handle-created :record
 :handle-conflict existing-user-error-msg
 :handle-malformed (fn [ctx] (ch/generate-string (get-in ctx [:errors]))))

(defresource authenticate [payload]
 :allowed-methods [:post]
 :malformed? (fn [ctx] (service/validate-user-login (retrieve-body payload)))
 :authorized? (fn [ctx] (service/valid-user? (retrieve-body payload)))
 :available-media-types ["application/json"]
 :post! (fn [ctx] (service/authenticate-user (retrieve-body payload)))
 :handle-created :record
 :handle-unauthorized login-error-msg
 :handle-malformed (fn [ctx] (ch/generate-string (get-in ctx [:errors]))))

(defresource user [email]
 :authorized? (fn [ctx] (service/is-authenticated? (retrieve-user-details (:request ctx))))
 :allowed-methods [:get]
 :available-media-types ["application/json"]
 :exists? {:record (service/get-user email)}
 :handle-ok record-in-ctx)