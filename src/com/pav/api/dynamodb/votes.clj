(ns com.pav.api.dynamodb.votes
  (:require [com.pav.api.dynamodb.db :as dy]
            [taoensso.faraday :as far]
            [clojure.tools.logging :as log]))

(defn create-vote-count [new-record]
  (try
    (far/put-item dy/client-opts dy/vote-count-table-name new-record)
    (catch Exception e (log/error e))))

(defn create-user-vote [vote]
  (far/put-item dy/client-opts dy/user-votes-table-name vote)
  vote)

(defn update-vote-count [bill_id vote]
  (let [update-stm (case vote
                     true {:update-expr "ADD #a :n" :expr-attr-names {"#a" "yes-count"} :expr-attr-vals {":n" 1}}
                     false {:update-expr "ADD #a :n" :expr-attr-names {"#a" "no-count"} :expr-attr-vals {":n" 1}})]
    (far/update-item dy/client-opts dy/vote-count-table-name {:bill_id bill_id} update-stm)))

(defn get-user-vote
  ([vote-id] (far/get-item dy/client-opts dy/user-votes-table-name {:vote-id vote-id}))
  ([bill_id user_id]
   (first (far/query dy/client-opts dy/user-votes-table-name {:user_id [:eq user_id] :bill_id [:eq bill_id]}
            {:return ["vote"] :index "user-bill-idx"}))))

(defn get-user-bill-vote [user_id bill_id]
  (first (far/query dy/client-opts dy/user-votes-table-name {:user_id [:eq user_id] :bill_id [:eq bill_id]}
     {:index "user-bill-idx"})))

(defn get-vote-count [bill_id]
  (far/get-item dy/client-opts dy/vote-count-table-name {:bill_id bill_id}))

(defn get-votes-for-bill [bill-id]
  (far/query dy/client-opts dy/user-votes-table-name {:bill_id [:eq bill-id]}
    {:index "bill-user-idx" :return [:timestamp :created_at :bill_id :vote :user_id]}))

(defn votes-count-between [start end]
  (->
    (far/scan dy/client-opts dy/user-votes-table-name
      {:attr-conds {:created_at [:between [start end]]}})
    meta
    :count))

(defn count-user-votes [user_id]
  (->
    (far/query dy/client-opts dy/user-votes-table-name {:user_id [:eq user_id]}
      {:index "user-bill-idx"})
    meta :count))

(defn retrieve-all-user-votes
  "Performs full table scan and retrieves all user vote records"
  []
  (loop [votes (far/scan dy/client-opts dy/user-votes-table-name)
         acc []]
    (if (:last-prim-kvs (meta votes))
      (recur (far/scan dy/client-opts dy/user-votes-table-name {:last-prim-kvs (:last-prim-kvs (meta votes))})
        (into acc votes))
      (into acc votes))))