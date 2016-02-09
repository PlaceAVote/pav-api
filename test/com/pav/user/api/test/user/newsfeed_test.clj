(ns com.pav.user.api.test.user.newsfeed-test
  (:use midje.sweet)
  (:require [cheshire.core :as ch]
            [com.pav.user.api.test.utils.utils :refer [flush-dynamo-tables
                                                       flush-redis
                                                       create-comment
                                                       flush-es-indexes
                                                       bootstrap-bills
                                                       test-user
                                                       pav-req]]))

(against-background [(before :facts (do
                                      (flush-redis)
                                      (flush-dynamo-tables)
                                      (flush-es-indexes)
                                      (bootstrap-bills)))]
  (fact "Retrieve current users feed"
      (let [{body :body} (pav-req :put "/user" test-user)
            {token :token} (ch/parse-string body true)
            {status :status body :body} (pav-req :get "/user/feed" token {})
            {last_timestamp :last_timestamp results :results} (ch/parse-string body true)]
        status => 200
        last_timestamp => (get-in (last results) [:timestamp])
        (count results) => 1)))
