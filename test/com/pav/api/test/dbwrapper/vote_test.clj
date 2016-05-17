(ns com.pav.api.test.dbwrapper.vote-test
  (:use midje.sweet)
  (:require [com.pav.api.test.utils.utils :as tu :refer [flush-sql-tables flush-selected-dynamo-tables select-values]]
            [com.pav.api.dbwrapper.helpers :refer [with-sql-backend-enabled]]
            [com.pav.api.dbwrapper.vote :as u]
            [com.pav.api.dynamodb.db :refer [user-table-name user-votes-table-name vote-count-table-name]]
            [com.pav.api.dynamodb.votes :as dynamodb]
            [com.pav.api.db.vote :as sql]
            [com.pav.api.services.votes :refer [new-vote-record]]))

(with-sql-backend-enabled
  (against-background [(before :facts (do
                                        (flush-selected-dynamo-tables [user-table-name
                                                                       user-votes-table-name
                                                                       vote-count-table-name])
                                        (flush-sql-tables)))]

    (facts "Test cases for Vote DBWrapper"

      (fact "Create a new bill comment associated with a user and validate data in both databases."
        (let [user (tu/create-user)
              vote-payload (new-vote-record {:bill_id "hr2-114" :vote true} (:user_id user))
              new-vote (u/create-user-vote-record vote-payload)
              dynamo-ret (dynamodb/get-user-vote (:vote-id new-vote))
              sql-ret (sql/get-user-vote-by-old-id (:vote-id new-vote))]
          (map? new-vote) => true
          (select-values dynamo-ret [:vote
                                     :bill_id
                                     :vote-id
                                     :user_id
                                     :timestamp
                                     :timestamp]) =>
          (select-values sql-ret [:vote
                                  :bill_id
                                  :old_vote_id
                                  :old_user_id
                                  :created_at
                                  :updated_at]))))))
