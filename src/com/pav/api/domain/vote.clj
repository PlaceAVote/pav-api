(ns com.pav.api.domain.vote
  (:require [schema.core :as s]
            [com.pav.api.schema.common :refer :all])
  (:import (java.util UUID Date)))

(s/defrecord NewDynamoVoteRecord
  [vote-id    :- str-schema
   user_id    :- str-schema
   bill_id    :- str-schema
   vote       :- s/Bool
   timestamp  :- Long
   created_at :- Long])

(s/defn ^:always-validate new-user-vote :- NewDynamoVoteRecord
  [vote user_info]
  (let [timestamp (.getTime (Date.))]
    (map->NewDynamoVoteRecord (assoc vote :vote-id (.toString (UUID/randomUUID)) :user_id (:user_id user_info)
                                          :timestamp timestamp :created_at timestamp))))
