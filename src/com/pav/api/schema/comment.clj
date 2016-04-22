(ns com.pav.api.schema.comment
  (:require [schema.core :as s]
            [com.pav.api.schema.common :refer :all]))

(def BillCommentRecord
  {:bill_id str-schema
   :body str-schema})

(def IssueCommentRecord
  {:issue_id str-schema
   :body str-schema})

(def IssueCommentRecordUpdate
  {:body str-schema})

(def BillCommentScoringRecord
  {:bill_id str-schema})

(def BilllCommentRecordUpdate
  {:body str-schema})

(defn valid-payload? [schema payload]
  (if (nil? (s/check schema payload))
    false
    true))

(defn new-bill-score-malformed? [payload]
  (valid-payload? BillCommentScoringRecord payload))

(defn new-bill-comment-malformed? [payload]
  (valid-payload? BillCommentRecord payload))

(defn bill-comment-update-malformed? [payload]
  (valid-payload? BilllCommentRecordUpdate payload))

(defn new-issue-comment-malformed? [payload]
  (valid-payload? IssueCommentRecord payload))

(defn update-issue-comment-malformed? [payload]
  (valid-payload? IssueCommentRecordUpdate payload))