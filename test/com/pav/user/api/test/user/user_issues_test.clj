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
           {token :token user_id :user_id } (ch/parse-string body true)
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
                                     :article_title :article_img] :in-any-order)))

   (fact "Add new emotional response"
     (let [{body :body} (pav-req :put "/user" test-user)
           {token :token user_id :user_id } (ch/parse-string body true)
           {status :status body :body} (pav-req :post "/user/issue/hr2-114/response" token
                                                {:emotional_response 1})
           response (ch/parse-string body true)]
       status => 201
       (:emotional_response response) => 1))

   (fact "Get emotional response"
     (let [{body :body} (pav-req :put "/user" test-user)
           {token :token user_id :user_id } (ch/parse-string body true)
           ;; save some data
           _ (pav-req :post "/user/issue/hr2-114/response" token {:emotional_response 1})
           ;; read it
           {status :status body :body} (pav-req :get "/user/issue/hr2-114/response" token {})
           response (ch/parse-string body true)]
       status => 200
       (:emotional_response response) => 1))

   (fact "Invalid emotional_response in POST"
     (let [{body :body} (pav-req :put "/user" test-user)
           {token :token user_id :user_id } (ch/parse-string body true)
           {status :status body :body} (pav-req :post "/user/issue/hr2-114/response" token
                                                {:emotional_response "junk"})]
       status => 400))

   (fact "No emotional_response in POST"
     (let [{body :body} (pav-req :put "/user" test-user)
           {token :token user_id :user_id } (ch/parse-string body true)
           {status :status body :body} (pav-req :post "/user/issue/hr2-114/response" token {})]
       status => 400)))
