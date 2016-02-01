(ns com.pav.user.api.test.user.user-issues-test
  (:use midje.sweet)
  (:require [com.pav.user.api.test.utils.utils :refer [flush-redis
                                                       flush-dynamo-tables
                                                       flush-user-index
                                                       test-user
                                                       pav-req]]
            [cheshire.core :as ch]))

(against-background [(before :facts (do
                                      (flush-dynamo-tables)
                                      (flush-redis)
                                      (flush-user-index)))]

   (fact "Add new issue"
     (let [{body :body} (pav-req :put "/user" test-user)
           {token :token} (ch/parse-string body true)
           {status :status body :body} (pav-req :put "/user/issue" token
                                                {:bill_id "hr2-114"
                                                 :comment "Comment Body goes here"
                                                 :article_link "http://medium.com/somethinginteresting"
                                                 :article_title "Interesting Article"
                                                 :article_img "http://medium.com/img/101"})
           response (ch/parse-string body true)]
       status => 201
       ;; part of response is :img_url, but midje is not able to take into account gaps present
       ;; on right side, but only left side, so this key is left out
       (keys response) => (contains [:user_id :first_name :last_name
                                     :bill_id :comment :article_link :issue_id
                                     :article_title :article_img] :in-any-order)
       ;; make sure all keys has values
       (some nil? (vals response)) => nil))

  (fact "Given a new issue, When payload does not contain comment, Then throw 400"
    (let [{body :body} (pav-req :put "/user" test-user)
          {token :token} (ch/parse-string body true)
          {status :status} (pav-req :put "/user/issue" token
                             {:bill_id "hr2-114"
                              :article_link "http://medium.com/somethinginteresting"
                              :article_title "Interesting Article"
                              :article_img "http://medium.com/img/101"})]
      status => 400))

   (fact "Add new emotional response"
     (let [{body :body} (pav-req :put "/user" test-user)
           {token :token} (ch/parse-string body true)
           {status :status body :body} (pav-req :post "/user/issue/hr2-114/response" token
                                                {:emotional_response 1})
           response (ch/parse-string body true)]
       status => 201
       (:emotional_response response) => 1))

   (fact "Get emotional response"
     (let [{body :body} (pav-req :put "/user" test-user)
           {token :token} (ch/parse-string body true)
           ;; save some data
           _ (pav-req :post "/user/issue/hr2-114/response" token {:emotional_response 1})
           ;; read it
           {status :status body :body} (pav-req :get "/user/issue/hr2-114/response" token {})
           response (ch/parse-string body true)]
       status => 200
       (:emotional_response response) => 1))

   (fact "Invalid emotional_response in POST"
     (let [{body :body} (pav-req :put "/user" test-user)
           {token :token} (ch/parse-string body true)
           {status :status} (pav-req :post "/user/issue/hr2-114/response" token
                                                {:emotional_response "junk"})]
       status => 400))

   (fact "No emotional_response in POST"
     (let [{body :body} (pav-req :put "/user" test-user)
           {token :token} (ch/parse-string body true)
           {status :status} (pav-req :post "/user/issue/hr2-114/response" token {})]
       status => 400))

  (fact "Given new issue, When article_link contains a resource with open graph data, Then parse and return graph data"
    (let [{body :body} (pav-req :put "/user" test-user)
          {token :token} (ch/parse-string body true)
          {status :status body :body} (pav-req :put "/user/issue" token
                                        {:bill_id "hr2-114"
                                         :comment "Comment Body goes here"
                                         :article_link "https://medium.com/the-trans-pacific-partnership/here-s-the-deal-the-text-of-the-trans-pacific-partnership-103adc324500#.mn7t24yff"})
          response (ch/parse-string body true)]
      status => 201
      (keys response) => (contains [:user_id :first_name :last_name
                                    :bill_id :comment :article_link :issue_id
                                    :article_title :article_img] :in-any-order)
      (select-keys response [:article_title :article_img :article_link])
        => (contains {:article_title "Here’s the Deal: The Text of the Trans-Pacific Partnership — The Trans-Pacific Partnership"
                      :article_link  "https://medium.com/the-trans-pacific-partnership/here-s-the-deal-the-text-of-the-trans-pacific-partnership-103adc324500"})
      (some nil? (vals response)) => nil))

  (fact "Given new issue, Then new issue should be in the users feed."
    (let [{body :body} (pav-req :put "/user" test-user)
          {token :token} (ch/parse-string body true)
          _ (pav-req :put "/user/issue" token
              {:bill_id "hr2-114"
               :comment "Comment Body goes here"
               :article_link "https://medium.com/the-trans-pacific-partnership/here-s-the-deal-the-text-of-the-trans-pacific-partnership-103adc324500#.mn7t24yff"})
          {status :status body :body} (pav-req :get "/user/feed" token {})
          response (:results (ch/parse-string body true))]
      status => 200
      (count response) => 1
      (some nil? (vals (first response))) => nil
      (keys (first response)) => (contains [:first_name :issue_id :author_id :bill_id :article_title :type
                                            :article_link :article_img :last_name :user_id :timestamp] :in-any-order))))
