(ns com.pav.api.schema.vote
  (:require [schema.core :as s]))

(def VoteRecord
  {:bill_id s/Str
   :vote s/Bool})

(defn new-vote-record-malformed? [vote]
  (if (nil? (s/check VoteRecord vote))
    false
    true))

