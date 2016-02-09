(ns com.pav.user.api.test.newsfeed.newsfeed-test
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
      (let [{body :body} (pav-req :put "/user" (assoc test-user :topics ["Defense" "Politics"]))
            {token :token} (ch/parse-string body true)
            {status :status body :body} (pav-req :get "/user/feed" token {})
            {last_timestamp :last_timestamp results :results} (ch/parse-string body true)]
        status => 200
        last_timestamp => (get-in (last results) [:timestamp])
        (count results) => 2))
  (fact "Retrieve current user feed, When from parameter is equal to the first items timestamp, Then return second item only"
    (let [{body :body} (pav-req :put "/user" (assoc test-user :topics ["Defense" "Politics"]))
          {token :token} (ch/parse-string body true)
          {body :body} (pav-req :get "/user/feed" token {})
          {results :results} (ch/parse-string body true)
          from (:timestamp (first results))
          {status :status body :body} (pav-req :get (str "/user/feed?from=" from) token {})
          {last_timestamp :last_timestamp results :results} (ch/parse-string body true)]
      status => 200
      (count results) => 1
      last_timestamp => (get-in (last results) [:timestamp]))))
