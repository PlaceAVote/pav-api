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
        (count body) => 8
        (last body) => (just {:date anything :vote_count 1 :comment_count 1 :user_count 1} :in-any-order)))))
