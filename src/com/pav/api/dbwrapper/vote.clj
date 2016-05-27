(ns com.pav.api.dbwrapper.vote
  (:require [com.pav.api.dbwrapper.helpers :refer [with-sql-backend bigint->long]]
            [com.pav.api.db.user :as sql-u]
            [com.pav.api.db.vote :as sql-v]
            [com.pav.api.utils.utils :refer [prog1]]
            [com.pav.api.dynamodb.votes :as dv]))

(defn dynamo-vote->sql-vote [{old_user_id :user_id timestamp :timestamp old_vote_id :vote-id :as vote}]
  (->
    (select-keys vote [:bill_id :vote])
    (assoc :old_user_id old_user_id
           :old_vote_id old_vote_id
           :user_id (:user_id (sql-u/get-user-by-old-id old_user_id))
           :created_at (bigint->long timestamp)
           :updated_at (bigint->long timestamp))))

(defn create-user-vote-record
  [vote]
  (prog1
    (dv/create-user-vote vote)
    (with-sql-backend
      (-> vote dynamo-vote->sql-vote sql-v/create-user-vote-record))))


