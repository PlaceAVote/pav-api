(ns com.pav.api.test.votes.votes-service-test
  (:use midje.sweet)
  (:require [com.pav.api.test.utils.utils :as u]
            [com.pav.api.services.votes :as vs]))

(against-background [(before :facts (do (u/flush-dynamo-tables)
                                        (u/flush-redis)))]

  (facts "Test cases to cover votes API."

    (fact "Record vote for user"
      (let [{user_id :user_id} (u/create-user)
            new-vote {:bill_id "hr2-114" :vote true}
            {vote-id :vote-id} (vs/create-user-vote-record new-vote user_id)
            record (vs/get-user-vote vote-id)
            vote-count (vs/get-vote-count "hr2-114")]
        record => (just {:user_id user_id :bill_id "hr2-114" :vote true :vote-id anything
                         :timestamp anything :created_at anything} :in-any-order)
        vote-count => {:bill_id "hr2-114" :yes-count 1 :no-count 0}))

    (fact "Vote with existing votes, ensure correct count"
      (let [user1 (u/create-user)
            user2 (u/create-user)
            first-vote {:bill_id "hr2-114" :vote true}
            second-vote {:bill_id "hr2-114" :vote false}]
        (vs/create-user-vote-record first-vote (:user_id user1))
        (vs/create-user-vote-record second-vote (:user_id user2))
        (vs/get-vote-count "hr2-114") => {:bill_id "hr2-114" :yes-count 1 :no-count 1}))

    (fact "Check if user has voted, should return true"
      (let [{user_id :user_id} (u/create-user)
            new-vote {:bill_id "hr2-114" :vote true}
            _ (vs/create-user-vote-record new-vote user_id)
            vote? (vs/has-user-voted? user_id "hr2-114")]
        vote? => true))

    (fact "Check if user has voted, should return false"
      (let [{user_id :user_id} (u/create-user)
            new-vote {:bill_id "hr2-114" :vote true}
            _ (vs/create-user-vote-record new-vote user_id)
            vote? (vs/has-user-voted? "3456" "hr2-114")]
        vote? => false))

    (fact "Retrieve all vote records associated a bill"
      (let [{user_id :user_id} (u/create-user)
            new-vote {:bill_id "hr2-114" :vote true}
            _ (vs/create-user-vote-record new-vote user_id)
            votes-for-bill (vs/get-votes-for-bill "hr2-114")]
        (count votes-for-bill) => 1
        (keys (first votes-for-bill)) => (just [:bill_id :vote :timestamp :created_at
                                                :age :district :gender :state] :in-any-order)))

    (fact "If there are no vote records associated with a bill, return an empty list"
      (vs/get-votes-for-bill "hr2-114") => [])

    (fact "If there are no vote counts associated with a bill, return an empty count payload"
      (vs/get-vote-count "hr2-114") => {:no-count 0 :yes-count 0})))
