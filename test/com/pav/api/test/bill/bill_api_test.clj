(ns com.pav.api.test.bill.bill-api-test
  (:use midje.sweet)
  (:require [com.pav.api.test.utils.utils :as u :refer [pav-req test-bills test-user bill-metadata]]
            [cheshire.core :as ch]))

(against-background [(before :facts (do (u/flush-es-indexes)
                                        (u/flush-dynamo-tables)
                                        (u/bootstrap-bills-and-metadata)
                                        (u/flush-redis)))]

  (facts "Bill API Test Cases"
    (fact "Retrieve bill by id"
      (let [expected (dissoc (merge (first test-bills) (first bill-metadata) {:user_voted false}) :_id)
            {body :body} (pav-req :put "/user" u/test-user)
            {token :token} (ch/parse-string body true)
            response (pav-req :get "/bills/hr2-114" token {})]
        (:status response) => 200
        (ch/parse-string (:body response) true) => (contains expected)))

    (fact "Retrieve bill by id, When user has voted against a bill, Then return user_voted = true."
      (let [expected (dissoc (merge (first test-bills) (first bill-metadata) {:user_voted true}) :_id)
            {body :body} (pav-req :put "/user" u/test-user)
            {token :token} (ch/parse-string body true)
            _ (pav-req :put "/vote" token {:bill_id "hr2-114" :vote true})
            response (pav-req :get "/bills/hr2-114" token {})]
        (:status response) => 200
        (ch/parse-string (:body response) true) => (contains expected)))

    (fact "Retrieve bill by id,  When a bill contains one comment Then return comment_count = 1."
      (let [test-comment {:bill_id "hr2-114" :body "comment goes here!!"}
            expected (dissoc (merge (first test-bills) (first bill-metadata) {:comment_count 1}) :_id)
            {body :body} (pav-req :put "/user" u/test-user)
            {token :token} (ch/parse-string body true)
            _ (pav-req :put "/bills/comments" token test-comment)
            response (pav-req :get "/bills/hr2-114" token {})]
        (:status response) => 200
        (ch/parse-string (:body response) true) => (contains expected)))

    (fact "Retrieve trending bills, ensure correct ordering"
      (let [{body :body} (pav-req :put "/user" u/test-user)
            {token :token} (ch/parse-string body true)
            _ (dotimes [_ 2] (pav-req :get "/bills/hr2-114" token {}))
            _ (dotimes [_ 5] (pav-req :get "/bills/s25-114" token {}))
            {status :status body :body} (pav-req :get "/bills/trending" token {})
            body (ch/parse-string body true)]
        status => 200
        (count body) => 2
        (first body) => (contains {:bill_id "s25-114" :comment_count 0 :yes-count 0 :no-count 0 :pav_topic "Economics"})))))
