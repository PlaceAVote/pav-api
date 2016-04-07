(ns com.pav.user.api.test.votes.votes-api-test
  (:use midje.sweet)
  (:require [com.pav.user.api.test.utils.utils :as u :refer [pav-req]]
            [cheshire.core :as ch]))

(against-background [(before :facts (do (u/flush-dynamo-tables)
                                        (u/flush-redis)))]
  (facts "Test cases to cover votes API."

    (fact "Given a vote request, process vote and return a 201 HTTP response"
      (let [{body :body} (pav-req :put "/user" u/test-user)
            {token :token} (ch/parse-string body true)
            new-vote {:bill_id "hr2-114" :vote true}
            {status :status body :body} (pav-req :put "/vote" token new-vote)]
        status => 201
        (ch/parse-string body true) => {:message "Vote created successfully"}))

    (fact "Given a vote request, with an invalid token, return 401 HTTP response"
      (let [{status :status} (pav-req :put "/vote" "token" {:bill_id "hr2-114" :vote true})]
        status => 401))

    (fact "Validate payload for missing bill-id, should return 400 HTTP response"
      (let [{body :body} (pav-req :put "/user" u/test-user)
            {token :token} (ch/parse-string body true)
            new-vote {:vote true}
            {status :status} (pav-req :put "/vote" token new-vote)]
        status => 400))

    (fact "Validate payload for missing vote, should return 400 HTTP response"
      (let [{body :body} (pav-req :put "/user" u/test-user)
            {token :token} (ch/parse-string body true)
            new-vote {:bill_id "hr2-114"}
            {status :status} (pav-req :put "/vote" token new-vote)]
        status => 400))

    (fact "Given a vote request, when user has already voted on bill then return 409 HTTP response"
      (let [{body :body} (pav-req :put "/user" u/test-user)
            {token :token} (ch/parse-string body true)
            new-vote {:bill_id "hr2-114" :vote true}
            _ (pav-req :put "/vote" token new-vote)
            {status :status body :body} (pav-req :put "/vote" token new-vote)]
        status => 409
        (ch/parse-string body true) => {:error "User has already voted on this issue"}))

    (fact "Retrieve vote count for a given bill"
      (let [{body :body} (pav-req :put "/user" u/test-user)
            {token :token} (ch/parse-string body true)
            new-vote {:bill_id "hr2-114" :vote true}
            _ (pav-req :put "/vote" token new-vote)
            count (pav-req :get "/vote/count?bill-id=hr2-114" token {})]
        (:status count) => 200
        (ch/parse-string (:body count) true) => {:bill_id "hr2-114" :yes-count 1 :no-count 0}))

    (fact "Retrieving vote count, When request has no Authentication Token, Then return count."
      (let [{body :body} (pav-req :put "/user" u/test-user)
            {token :token} (ch/parse-string body true)
            new-vote {:bill_id "hr2-114" :vote true}
            _ (pav-req :put "/vote" token new-vote)
            count (pav-req :get "/vote/count?bill-id=hr2-114")]
        (:status count) => 200
        (ch/parse-string (:body count) true) => {:bill_id "hr2-114" :yes-count 1 :no-count 0}))

    (fact "Retrieve vote records for a given bill"
      (let [{body :body} (pav-req :put "/user" u/test-user)
            {token :token} (ch/parse-string body true)
            new-vote {:bill_id "hr2-114" :vote true}
            _ (pav-req :put "/vote" token new-vote)
            {status :status body :body} (pav-req :get "/vote/bill/hr2-114" token {})
            response (first (ch/parse-string body true))]
        status => 200
        (keys response) => (contains [:bill_id :vote :timestamp :created_at] :in-any-order)
        response => (contains {:bill_id "hr2-114" :vote true})))

    (fact "Retrieving vote records with no Authentication Token, then return vote records for given bill"
      (let [{body :body} (pav-req :put "/user" u/test-user)
            {token :token} (ch/parse-string body true)
            new-vote {:bill_id "hr2-114" :vote true}
            _ (pav-req :put "/vote" token new-vote)
            {status :status body :body} (pav-req :get "/vote/bill/hr2-114")]
        status => 200
        (first (ch/parse-string body true)) => (contains {:bill_id "hr2-114"
                                                          :vote    true})))

    (fact "When retrieving vote records for a bill that doesn't exist, return empty list"
      (let [{body :body} (pav-req :get "/vote/bill/hr2-114")]
        (ch/parse-string body true) => []))))
