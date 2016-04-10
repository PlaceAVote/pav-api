(ns com.pav.api.resources.docs
  (:require [liberator.core :refer [resource defresource]]
            [liberator.representation :refer [ring-response]]
            [com.pav.api.schema.user :refer [User UserRecord]]))

(def user-docs
  (slurp "resources/swagger/swagger.json"))

(defresource swagger-docs
  :available-media-types ["application/json"]
  :handle-ok user-docs)
