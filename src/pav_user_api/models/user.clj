(ns pav-user-api.models.user
  (:require [liberator.core :refer [resource defresource]]
            [pav-user-api.services.users :as service]))

(defresource list-users
 :available-media-types ["application/json"]
 :handle-ok (service/get-users))
