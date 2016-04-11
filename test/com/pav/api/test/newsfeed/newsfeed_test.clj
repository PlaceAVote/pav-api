(ns com.pav.api.test.newsfeed.newsfeed-test
  (:use midje.sweet)
  (:require [cheshire.core :as ch]
            [com.pav.api.migrations.migrations :as m]
            [com.pav.api.test.utils.utils :refer [flush-dynamo-tables
                                                       flush-redis
                                                       create-comment
                                                       flush-es-indexes
                                                       bootstrap-bills-and-metadata
                                                       test-user
                                                       pav-req]]))

(against-background [(before :facts (do
                                      (flush-redis)
                                      (flush-dynamo-tables)
                                      (flush-es-indexes)
                                      (bootstrap-bills-and-metadata)))]
  (fact "Retrieve current users feed"
      (let [{body :body} (pav-req :put "/user" (assoc test-user :topics ["Healthcare"]))
            {token :token} (ch/parse-string body true)
            {status :status body :body} (pav-req :get "/user/feed" token {})
            {last_timestamp :last_timestamp results :results} (ch/parse-string body true)]
        status => 200
        last_timestamp => (get-in (last results) [:timestamp])
        (count results) => 1))

  (fact "Retrieve current user feed, When from parameter is equal to the first items timestamp, Then return second item only"
    (let [{body :body} (pav-req :put "/user" (assoc test-user :topics ["Healthcare" "Economics"]))
          {token :token} (ch/parse-string body true)
          {body :body} (pav-req :get "/user/feed" token {})
          {results :results} (ch/parse-string body true)
          from (:timestamp (first results))
          {status :status body :body} (pav-req :get (str "/user/feed?from=" from) token {})
          {last_timestamp :last_timestamp results :results} (ch/parse-string body true)]
      status => 200
      (count results) => 1
      last_timestamp => (get-in (last results) [:timestamp]))))
