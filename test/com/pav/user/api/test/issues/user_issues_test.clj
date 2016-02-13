(ns com.pav.user.api.test.issues.user-issues-test
  (:use midje.sweet)
  (:require [com.pav.user.api.test.utils.utils :refer [flush-redis
                                                       flush-dynamo-tables
                                                       flush-es-indexes
                                                       bootstrap-bills
                                                       test-user
                                                       pav-req]]
            [cheshire.core :as ch]))

(def follower {:email "peter@pl.com" :password "stuff2" :first_name "peter" :last_name "pan" :dob "05/10/1984"
               :country_code "USA" :topics ["Defense"] :gender "male"})

(against-background [(before :facts (do
                                      (flush-dynamo-tables)
                                      (flush-redis)
                                      (flush-es-indexes)
                                      (bootstrap-bills)))]

   (fact "Add new issue"
     (let [{body :body} (pav-req :put "/user" test-user)
           {token :token} (ch/parse-string body true)
           {status :status body :body} (pav-req :put "/user/issue" token
                                                {:bill_id "hr2-114"
                                                 :comment "Comment Body goes here"
                                                 :article_link "http://time.com/3319278/isis-isil-twitter/"})
           response (ch/parse-string body true)]
       status => 201
       ;; part of response is :img_url, but midje is not able to take into account gaps present
       ;; on right side, but only left side, so this key is left out
       (keys response) => (contains [:user_id :first_name :last_name
                                     :bill_id :bill_title :comment :article_link :issue_id
                                     :article_title :article_img :emotional_response
                                     :positive_responses :negative_responses :neutral_responses] :in-any-order)
       ;; make sure all keys has values
       (some nil? (vals response)) => nil))

  (fact "Given a new issue, When payload only contains a comment, Then process issue"
    (let [{body :body} (pav-req :put "/user" test-user)
          {token :token} (ch/parse-string body true)
          {status :status} (pav-req :put "/user/issue" token
                             {:comment "Comment goes here!!!"})]
      status => 201))

  (fact "Try creating a new issue with an empty payload, Then return 400 error"
    (let [{body :body} (pav-req :put "/user" test-user)
          {token :token} (ch/parse-string body true)
          {status :status} (pav-req :put "/user/issue" token {})]
      status => 400))

  (fact "Given a new issue, When payload has a bill_id without a comment, Then throw 400 error"
    (let [{body :body} (pav-req :put "/user" test-user)
          {token :token} (ch/parse-string body true)
          {status :status} (pav-req :put "/user/issue" token
                             {:bill_id "hr2-114"})]
      status => 400))

  (fact "Given a new issue, When payload does not contain comment but bill id and article information, Then process issue"
    (let [{body :body} (pav-req :put "/user" test-user)
          {token :token} (ch/parse-string body true)
          {status :status} (pav-req :put "/user/issue" token
                             {:bill_id "hr2-114"
                              :article_link "http://medium.com/somethinginteresting"})]
      status => 201))

  (fact "Given a new issue, When payload contains only an article link, Then process issue"
    (let [{body :body} (pav-req :put "/user" test-user)
          {token :token} (ch/parse-string body true)
          {status :status} (pav-req :put "/user/issue" token
                             {:article_link "http://medium.com/somethinginteresting"})]
      status => 201))

   (fact "Add new emotional response, to existing issue"
     (let [{body :body}   (pav-req :put "/user" test-user)
           {token :token} (ch/parse-string body true)
           {body :body}   (pav-req :put "/user/issue" token
                            {:bill_id "hr2-114"
                             :comment "Goes here."
                             :article_link "http://medium.com/somethinginteresting"})
           {issue_id :issue_id} (ch/parse-string body true)
           {status :status body :body} (pav-req :post (str "/user/issue/" issue_id "/response") token
                                                {:emotional_response "positive"})
           response (ch/parse-string body true)]
       status => 201
       (:emotional_response response) => "positive"))

  (fact "Add new emotional response, When issue_id is invalid, Then throw 400 error"
    (let [{body :body}   (pav-req :put "/user" test-user)
          {token :token} (ch/parse-string body true)
          {status :status} (pav-req :post "/user/issue/invalidID/response" token
                             {:emotional_response "positive"})]
      status => 400))

   (fact "Get emotional response, when user has responded negatively, Then return negative response"
     (let [{body :body} (pav-req :put "/user" test-user)
           {token :token} (ch/parse-string body true)
           ;;create issue
           {body :body}   (pav-req :put "/user/issue" token {:comment "Goes here."})
           {issue_id :issue_id} (ch/parse-string body true)
           ;; save some data
           _ (pav-req :post (str "/user/issue/" issue_id "/response") token {:emotional_response "negative"})
           ;; read it
           {status :status body :body} (pav-req :get (str "/user/issue/" issue_id "/response") token {})
           response (ch/parse-string body true)]
       status => 200
       (:emotional_response response) => "negative"))

  (fact "Get emotional response, when user hasn't responded, Then return none as emotional response"
    (let [{body :body} (pav-req :put "/user" test-user)
          {token :token} (ch/parse-string body true)
          ;;create issue
          {body :body}   (pav-req :put "/user/issue" token {:comment "Goes here."})
          {issue_id :issue_id} (ch/parse-string body true)
          ;; read it
          {status :status body :body} (pav-req :get (str "/user/issue/" issue_id "/response") token {})
          response (ch/parse-string body true)]
      status => 200
      (:emotional_response response) => "none"))

   (fact "Invalid emotional_response in POST"
     (let [{body :body} (pav-req :put "/user" test-user)
           {token :token} (ch/parse-string body true)
           {body :body}   (pav-req :put "/user/issue" token {:comment "Goes here."})
           {issue_id :issue_id} (ch/parse-string body true)
           {status :status} (pav-req :post (str "/user/issue/" issue_id "/response") token
                                                {:emotional_response "junk"})]
       status => 400))

  (fact "Delete emotional_response"
    (let [{body :body} (pav-req :put "/user" test-user)
          {token :token} (ch/parse-string body true)
          ;;create issue
          {body :body}   (pav-req :put "/user/issue" token {:comment "Goes here."})
          {issue_id :issue_id} (ch/parse-string body true)
          ;;respond negatively
          _ (pav-req :post (str "/user/issue/" issue_id "/response") token {:emotional_response "negative"})
          ;;delete negative response
          {delete-status :status} (pav-req :delete (str "/user/issue/" issue_id "/response") token {})
          ;; read it
          {status :status body :body} (pav-req :get (str "/user/issue/" issue_id "/response") token {})
          response (ch/parse-string body true)]
      delete-status => 204
      status => 200
      (:emotional_response response) => "none"))

  (fact "Delete emotional_response, When deleting emotional response twice, ensure score is not minus in users feed."
    (let [{body :body} (pav-req :put "/user" test-user)
          {token :token} (ch/parse-string body true)
          ;;create issue
          {body :body}   (pav-req :put "/user/issue" token {:comment "Goes here."})
          {issue_id :issue_id} (ch/parse-string body true)
          ;;respond negatively
          _ (pav-req :post (str "/user/issue/" issue_id "/response") token {:emotional_response "negative"})
          ;;try deleting negative response
          _ (pav-req :delete (str "/user/issue/" issue_id "/response") token {})
          _ (pav-req :delete (str "/user/issue/" issue_id "/response") token {})
          {body :body} (pav-req :get "/user/feed" token {})
          response (first (:results (ch/parse-string body true)))]
      (:negative_responses response) => 0))

   (fact "No emotional_response in POST"
     (let [{body :body} (pav-req :put "/user" test-user)
           {token :token} (ch/parse-string body true)
           {body :body}   (pav-req :put "/user/issue" token {:comment "Goes here."})
           {issue_id :issue_id} (ch/parse-string body true)
           {status :status} (pav-req :post (str "/user/issue/" issue_id "/response") token {})]
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
                                    :bill_id :bill_title :comment :article_link :issue_id
                                    :article_title :article_img :emotional_response
                                    :positive_responses :negative_responses :neutral_responses] :in-any-order)
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
          response (first (:results (ch/parse-string body true)))]
      status => 200
      (some nil? (vals response)) => nil
      (keys response) => (contains [:first_name :last_name :user_id :timestamp :issue_id :author_id
                                    :article_title :type :bill_id :bill_title :comment
                                    :article_link :article_img :emotional_response
                                    :positive_responses :negative_responses :neutral_responses] :in-any-order)))

  (fact "Given new issue, Then new issue should be in the users activity timeline."
    (let [{body :body} (pav-req :put "/user" test-user)
          {token :token} (ch/parse-string body true)
          _ (pav-req :put "/user/issue" token
              {:bill_id "hr2-114"
               :comment "Comment Body goes here"
               :article_link "https://medium.com/the-trans-pacific-partnership/here-s-the-deal-the-text-of-the-trans-pacific-partnership-103adc324500#.mn7t24yff"})
          {status :status body :body} (pav-req :get "/user/me/timeline" token {})
          response (first (:results (ch/parse-string body true)))]
      status => 200
      (some nil? (vals response)) => nil
      (keys response) => (contains [:first_name :last_name :user_id :timestamp :issue_id :author_id
                                    :article_title :type :bill_id :bill_title :comment
                                    :article_link :article_img :emotional_response :event_id
                                    :positive_responses :negative_responses :neutral_responses] :in-any-order)))

  (fact "Given new issue, When user responses positively, Then issue should have an emotional response for the given user in the feed item."
    (let [{body :body} (pav-req :put "/user" test-user)
          {token :token} (ch/parse-string body true)
          {body :body} (pav-req :put "/user/issue" token {:comment "Comment Body goes here"})
          {issue_id :issue_id} (ch/parse-string body true)
          _ (pav-req :post (str "/user/issue/" issue_id "/response") token {:emotional_response "neutral"})
          {status :status body :body} (pav-req :get "/user/feed" token {})
          response (first (:results (ch/parse-string body true)))]
      status => 200
      (some nil? (vals response)) => nil
      (keys response) => (contains [:emotional_response] :in-any-order)
      (:neutral_responses response) => 1))

  (fact "Given a new issue, When user has followers, Then verify the issue appears in the followers feed."
    (let [{body :body} (pav-req :put "/user" follower)
          {follower_token :token} (ch/parse-string body true)
          {body :body} (pav-req :put "/user" test-user)
          {token :token user_id :user_id} (ch/parse-string body true)
          ;;follow user
          _ (pav-req :put (str "/user/follow") follower_token {:user_id user_id})
          ;;publish issue
          _(pav-req :put "/user/issue" token {:comment "Comment Body goes here"})
          ;;retrieve followers feed
          {status :status body :body} (pav-req :get "/user/feed" follower_token {})
          response (first (:results (ch/parse-string body true)))]
      status => 200
      (some nil? (vals response)) => nil
      (keys response) => (contains [:first_name :last_name :user_id :timestamp :issue_id :author_id
                                    :comment :emotional_response :type
                                    :positive_responses :negative_responses :neutral_responses] :in-any-order)))

  (fact "Given an emotional response, When the user is from another user, Then verify response is in authors notification feed"
    (let [{body :body} (pav-req :put "/user" follower)
          {follower_token :token} (ch/parse-string body true)
          {body :body} (pav-req :put "/user" test-user)
          {token :token} (ch/parse-string body true)
          ;;publish issue
          {body :body} (pav-req :put "/user/issue" token {:comment "Comment Body goes here" :bill_id "hr2-114"})
          {issue_id :issue_id} (ch/parse-string body true)
          ;;follower responds positively
          _ (pav-req :post (str "/user/issue/" issue_id "/response") follower_token {:emotional_response "positive"})
          ;;retrieve authors notification feed
          {body :body} (pav-req :get "/user/notifications" token {})
          {results :results} (ch/parse-string body true)]
      (some nil? (vals (first results))) => nil
      (keys (first results)) => (contains [:bill_id :bill_title :first_name :last_name :emotional_response :user_id :author :timestamp
                                          :type :notification_id] :in-any-order)))

  (fact "Given an emotional response, When the user is also the author, Then verify no response is present in the users notification feed"
    (let [{body :body} (pav-req :put "/user" test-user)
          {token :token} (ch/parse-string body true)
          ;;publish issue
          {body :body} (pav-req :put "/user/issue" token {:comment "Comment Body goes here" :bill_id "hr2-114"})
          {issue_id :issue_id} (ch/parse-string body true)
          ;;user responds positively to his own issue
          _ (pav-req :post (str "/user/issue/" issue_id "/response") token {:emotional_response "positive"})
          ;;retrieve users notification feed
          {body :body} (pav-req :get "/user/notifications" token {})
          {results :results} (ch/parse-string body true)]
      results => [])))
