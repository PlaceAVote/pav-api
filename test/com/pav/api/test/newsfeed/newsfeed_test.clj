(ns com.pav.api.test.newsfeed.newsfeed-test
  (:use midje.sweet)
  (:require [com.pav.api.test.utils.utils :refer [flush-dynamo-tables
                                                  flush-redis
                                                  create-comment
                                                  flush-es-indexes
                                                  bootstrap-bills-and-metadata
                                                  new-pav-user
                                                  pav-reqv2]]))

(against-background [(before :contents (do
                                         (flush-redis)
                                         (flush-dynamo-tables)
                                         (flush-es-indexes)
                                         (bootstrap-bills-and-metadata)))]
  (fact "Retrieve current users feed"
      (let [{body :body} (pav-reqv2 :put "/user" (new-pav-user {:topics ["Healthcare"]}))
            {token :token} body
            {status :status body :body} (pav-reqv2 :get "/user/feed" token {})
            {last_timestamp :last_timestamp results :results} body]
        status => 200
        last_timestamp => (get-in (last results) [:timestamp])
        (count results) => 1))

  (fact "Retrieve current user feed, When from parameter is equal to the first items timestamp, Then return second item only"
    (let [{body :body} (pav-reqv2 :put "/user" (new-pav-user {:topics ["Healthcare" "Economics"]}))
          {token :token} body
          {body :body} (pav-reqv2 :get "/user/feed" token {})
          {results :results} body
          from (:timestamp (first results))
          {status :status body :body} (pav-reqv2 :get (str "/user/feed?from=" from) token {})
          {last_timestamp :last_timestamp results :results} body]
      status => 200
      (count results) => 1
      last_timestamp => (get-in (last results) [:timestamp]))))
