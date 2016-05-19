(ns com.pav.api.dynamodb.common
  (:require [com.pav.api.dynamodb.db :as dy]
            [taoensso.faraday :as far]))

(defn batch-delete-from-feed
  [batch]
  (doseq [b (partition 25 25 nil batch)]
    (far/batch-write-item dy/client-opts {dy/userfeed-table-name {:delete b}})))

(defn full-table-scan
  "Performs a full table scan and returns all records for given table name
  WARNING!!! this function recursively scans the table until it gets all the records,
  if your table is large and contains thousands of records, refrain from using this function."
  ([table]
   (loop [records (far/scan dy/client-opts table)
          acc []]
     (if (:last-prim-kvs (meta records))
       (recur (far/scan dy/client-opts table {:last-prim-kvs (:last-prim-kvs (meta records))})
         (into acc records))
       (into acc records)))))