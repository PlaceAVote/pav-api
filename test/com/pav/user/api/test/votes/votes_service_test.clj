(ns com.pav.user.api.test.votes.votes-service-test
  (:use midje.sweet)
  (:require [com.pav.user.api.test.utils.utils :as u]
            [com.pav.user.api.services.votes :as vs])
  (:import (java.util UUID Date)))

(against-background [(before :facts (do (u/flush-dynamo-tables)
                                        (u/flush-redis)))]
  (facts "Test cases to cover votes API."

    (fact "Record vote for user"
      (let [vote-id (.toString (UUID/randomUUID))
            new-vote {:user_id "12345" :bill_id "hr2-114" :vote true :vote-id vote-id :timestamp (.getTime (Date.))}
            _ (vs/create-user-vote-record new-vote)
            record (vs/get-user-vote vote-id)
            vote-count (vs/get-vote-count "hr2-114")]
        record => new-vote
        vote-count => {:bill_id "hr2-114" :yes-count 1 :no-count 0})))

  (fact "Vote with existing votes, ensure correct count"
    (let [first-vote {:user_id "12345" :bill_id "hr2-114" :vote true :vote-id (.toString (UUID/randomUUID)) :timestamp (.getTime (Date.))}
          second-vote  {:user_id "12354" :bill_id "hr2-114" :vote false :vote-id (.toString (UUID/randomUUID)) :timestamp (.getTime (Date.))}]
      (vs/create-user-vote-record first-vote)
      (vs/create-user-vote-record second-vote)
      (vs/get-vote-count "hr2-114") => {:bill_id "hr2-114" :yes-count 1 :no-count 1}))

  (fact "Check if user has voted, should return true"
    (let [new-vote {:user_id "12345" :bill_id "hr2-114" :vote true :vote-id (.toString (UUID/randomUUID)) :timestamp (.getTime (Date.))}
          _ (vs/create-user-vote-record new-vote)
          vote? (vs/has-user-voted? "12345" "hr2-114")]
      vote? => true))

  (fact "Check if user has voted, should return false"
    (let [new-vote {:user_id "12345" :bill_id "hr2-114" :vote true :vote-id (.toString (UUID/randomUUID)) :timestamp (.getTime (Date.))}
          _ (vs/create-user-vote-record new-vote)
          vote? (vs/has-user-voted? "3456" "hr2-114")]
      vote? => false))

  (fact "Retrieve all vote records associated a bill"
    (let [new-vote {:user_id "12345" :bill_id "hr2-114" :vote true :vote-id (.toString (UUID/randomUUID))
                    :created_at (.getTime (Date.)) :timestamp (.getTime (Date.))}
          _ (vs/create-user-vote-record new-vote)
          votes-for-bill (vs/get-votes-for-bill "hr2-114")]
      (count votes-for-bill) => 1
      (keys (first votes-for-bill)) => (contains [:bill_id :vote :timestamp :created_at] :in-any-order)
      (first votes-for-bill) => (dissoc new-vote :vote-id :user_id)))

  (fact "If there are no vote records associated with a bill, return an empty list"
    (vs/get-votes-for-bill "hr2-114") => [])

  (fact "If there are no vote counts associated with a bill, return an empty count payload"
    (vs/get-vote-count "hr2-114") => {:no-count 0 :yes-count 0}))
