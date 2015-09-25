(ns com.pav.user.api.resources.docs
  (:require [liberator.core :refer [resource defresource]]
            [liberator.representation :refer [ring-response]]
            [ring.swagger.swagger2 :as rs]
            [com.pav.user.api.schema.user :refer [User UserRecord]]))

(def user-docs
  (slurp "resources/swagger/swagger.json"))

(defresource swagger-docs
  :available-media-types ["application/json"]
  :handle-ok user-docs)
