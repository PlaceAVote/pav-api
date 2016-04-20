(ns com.pav.api.dynamodb.common
  (:require [com.pav.api.dynamodb.db :as dy]
            [taoensso.faraday :as far]))

(defn batch-delete-from-feed
  [batch]
  (doseq [b (partition 25 25 nil batch)]
    (far/batch-write-item dy/client-opts {dy/userfeed-table-name {:delete b}})))