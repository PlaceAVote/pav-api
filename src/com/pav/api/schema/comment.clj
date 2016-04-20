(ns com.pav.api.schema.comment
  (:require [schema.core :as s]
            [com.pav.api.schema.common :refer :all]))

(def CommentRecord
  {:bill_id str-schema
   :body str-schema})

(def ScoringRecord
  {:bill_id str-schema})

(def CommentRecordUpdate
  {:body str-schema})

(defn valid-payload? [schema payload]
  (if (nil? (s/check schema payload))
    false
    true))

(defn new-score-malformed? [payload]
  (valid-payload? ScoringRecord payload))

(defn new-comment-malformed? [payload]
  (valid-payload? CommentRecord payload))

(defn comment-update-malformed? [payload]
  (valid-payload? CommentRecordUpdate payload))