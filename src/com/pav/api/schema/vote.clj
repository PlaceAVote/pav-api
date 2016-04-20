(ns com.pav.api.schema.vote
  (:require [schema.core :as s]
            [com.pav.api.schema.common :refer :all]))

(def VoteRecord
  {:bill_id str-schema
   :vote s/Bool})

(defn new-vote-record-malformed? [vote]
  (if (nil? (s/check VoteRecord vote))
    false
    true))

