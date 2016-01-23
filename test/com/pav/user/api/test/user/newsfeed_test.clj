(ns com.pav.user.api.test.user.newsfeed-test
  (:use midje.sweet)
  (:require [cheshire.core :as ch]
            [com.pav.user.api.test.utils.utils :refer [flush-dynamo-tables
                                                       flush-redis
                                                       create-comment
                                                       flush-user-index
                                                       bootstrap-bills
                                                       test-user
                                                       pav-req]]))

(against-background [(before :facts (do
                                      (flush-redis)
                                      (flush-dynamo-tables)
                                      (flush-user-index)
                                      (bootstrap-bills)))]
	(fact "Retrieve current users feed"
		(let [_ (Thread/sleep 3000)
					{body :body} (pav-req :put "/user" (assoc test-user :topics ["Defense" "Politics"]))
					{token :token} (ch/parse-string body true)
					_ (create-comment {:comment_id "101" :bill_id "hr1764-114"})
					_ (Thread/sleep 1000)
					{status :status body :body} (pav-req :get "/user/feed" token {})
					{next-page :next-page} (ch/parse-string body true)]
			status => 200
			next-page => 0)))
