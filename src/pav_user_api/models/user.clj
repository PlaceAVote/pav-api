(ns pav-user-api.models.user
  (:require [liberator.core :refer [resource defresource]]
            [pav-user-api.services.users :as service]
            [pav-user-api.utils.utils :refer [record-in-ctx retrieve-params]]))

(defresource list-users
 :available-media-types ["application/json"]
 :handle-ok (service/get-users))

(defresource create [payload]
 :allowed-methods [:put]
 :available-media-types ["application/json"]
 :put! (service/create-user (retrieve-params payload))
 :handle-created :record)

(defresource user [email]
 :allowed-methods [:get]
 :available-media-types ["application/json"]
 :exists? {:record (service/get-user email)}
 :handle-ok record-in-ctx)
