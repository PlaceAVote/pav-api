(ns com.pav.user.api.resources.bill
  (:require [liberator.core :refer [resource defresource]]
            [com.pav.user.api.services.bills :as bs]
            [com.pav.user.api.utils.utils :as u]))

(defresource get-bill [bill_id]
  :allowed-methods [:get]
  :available-media-types ["application/json"]
  :exists? (fn [ctx] {:record (bs/get-bill bill_id (u/retrieve-token-user-id ctx))})
  :handle-ok :record)

(defresource get-trending-bills
  :allowed-methods [:get]
  :available-media-types ["application/json"]
  :handle-ok (bs/trending-bills))
