(ns com.pav.api.test.reports.reports-test
  (:use midje.sweet)
  (:require [com.pav.api.test.utils.utils :refer [flush-dynamo-tables
                                                  flush-redis
                                                  flush-es-indexes
                                                  bootstrap-bills-and-metadata
                                                  pav-req
                                                  new-pav-user
                                                  new-fb-user]]))

(against-background [(before :contents (do (flush-dynamo-tables)
                                           (flush-redis)
                                           (flush-es-indexes)
                                           (bootstrap-bills-and-metadata)))]

  (facts "Small test case for reporting"

    (fact "Testing reporting activity job"
      (let [{body :body} (pav-req :put "/user" (new-pav-user))
            {token :token} body
            _ (pav-req :put "/vote" token {:bill_id "hr2-114" :vote true})
            _ (pav-req :put "/bills/comments" token {:bill_id "hr2-114" :body "comment body"})
            {status :status body :body} (pav-req :get "/reports/activity?weeks=1")]
        status => 200
        (:total_signups body) => 1
        (:total_votes body) => 1
        (:total_comments body) => 1
        (count (:results body)) => 8
        (last (:results body)) => (just {:date anything :vote_count 1 :comment_count 1 :signup_count 1} :in-any-order)))))
