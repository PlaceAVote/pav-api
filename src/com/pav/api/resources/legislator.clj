(ns com.pav.api.resources.legislator
  (:require [liberator.core :refer [resource defresource]]
            [com.pav.api.elasticsearch.user :as es]))

(defresource get-legislator [thomas]
  :allowed-methods [:get]
  :available-media-types ["application/json"]
  :exists? {:record (es/get-legislator thomas)}
  :handle-ok :record)