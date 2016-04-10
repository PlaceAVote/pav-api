(ns com.pav.api.resources.search
  (:require [liberator.core :refer [resource defresource]]
            [com.pav.api.elasticsearch.user :as es]))

(defresource search-term [term]
  :allowed-methods [:get]
  :available-media-types ["application/json"]
  :handle-ok (es/search-for-term term))
