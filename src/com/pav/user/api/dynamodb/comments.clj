(ns com.pav.user.api.dynamodb.comments
  (:require [taoensso.faraday :as far]
            [com.pav.user.api.dynamodb.db :as dy]))

(defn add-bill-comment-count [{:keys [bill_id] :as event}]
  (let [ccount (count (far/query dy/client-opts dy/comment-details-table-name {:bill_id [:eq bill_id]} {:index "bill-comment-idx"}))]
    (assoc event :comment_count ccount)))