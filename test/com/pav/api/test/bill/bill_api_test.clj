(ns com.pav.api.test.bill.bill-api-test
  (:use midje.sweet)
  (:require [com.pav.api.test.utils.utils :as u :refer [pav-req test-bills new-pav-user bill-metadata]]))

(against-background [(before :facts (do (u/flush-es-indexes)
                                        (u/flush-dynamo-tables)
                                        (u/bootstrap-bills-and-metadata)
                                        (u/flush-redis)))]

  (facts "Bill API Test Cases"
    (fact "Retrieve bill by id"
      (let [expected (dissoc (merge (first test-bills) (first bill-metadata) {:user_voted false}) :_id)
            {body :body} (pav-req :put "/user" (new-pav-user))
            {token :token} body
            {status :status body :body} (pav-req :get "/bills/hr2-114" token {})]
        status => 200
        body => (contains expected)))

    (fact "Retrieve bill by id, When user has voted against a bill, Then return user_voted = true."
      (let [expected (dissoc (merge (first test-bills) (first bill-metadata) {:user_voted true}) :_id)
            {body :body} (pav-req :put "/user" (new-pav-user))
            {token :token} body
            _ (pav-req :put "/vote" token {:bill_id "hr2-114" :vote true})
            {status :status body :body} (pav-req :get "/bills/hr2-114" token {})]
        status => 200
        body => (contains expected)))

    (fact "Retrieve bill by id,  When a bill contains one comment Then return comment_count = 1."
      (let [test-comment {:bill_id "hr2-114" :body "comment goes here!!"}
            expected (dissoc (merge (first test-bills) (first bill-metadata) {:comment_count 1}) :_id)
            {body :body} (pav-req :put "/user" (new-pav-user))
            {token :token} body
            _ (pav-req :put "/bills/comments" token test-comment)
            {status :status body :body} (pav-req :get "/bills/hr2-114" token {})]
        status => 200
        body => (contains expected)))

    (fact "Retrieve trending bills, ensure correct ordering"
      (let [test-comment {:bill_id "s25-114" :body "comment goes here!!"}
            {body :body} (pav-req :put "/user" (new-pav-user))
            {token :token} body
            _ (pav-req :put "/bills/comments" token test-comment)
            _ (dotimes [_ 2] (pav-req :get "/bills/hr2-114" token {}))
            _ (dotimes [_ 5] (pav-req :get "/bills/s25-114" token {}))
            {status :status body :body} (pav-req :get "/bills/trending" token {})
            body body]
        status => 200
        (count body) => 2
        (first body) => (contains {:bill_id "s25-114" :comment_count 1 :yes-count 0 :no-count 0 :pav_topic "Economics"})))))
