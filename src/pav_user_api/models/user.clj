(ns pav-user-api.models.user
  (:require [liberator.core :refer [resource defresource]]
            [pav-user-api.services.users :as service]
            [pav-user-api.utils.utils :refer [record-in-ctx retrieve-body]]
            [cheshire.core :as ch]))

(def existing-user-error-msg {:error "A User already exists with this email"})

(defresource list-users
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

(defresource user [email]
 :allowed-methods [:get]
 :available-media-types ["application/json"]
 :exists? {:record (service/get-user email)}
 :handle-ok record-in-ctx)
