(ns com.pav.api.test.issues.user-issues-test
  (:use midje.sweet)
  (:require [com.pav.api.test.utils.utils :refer [flush-redis
                                                       flush-dynamo-tables
                                                       flush-es-indexes
                                                       bootstrap-bills-and-metadata
                                                       test-user
                                                       pav-req
                                                       test-fb-user]]
            [cheshire.core :as ch]))

(def follower {:email "peter@pl.com" :password "stuff2" :first_name "peter" :last_name "pan" :dob "05/10/1984"
               :topics ["Defense"] :gender "male" :zipcode "12345"})

(against-background [(before :facts (do
                                      (flush-dynamo-tables)
                                      (flush-redis)
                                      (flush-es-indexes)
                                      (bootstrap-bills-and-metadata)))]

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
                                     :short_issue_id :article_title :article_img :emotional_response
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
                                    :bill_id :bill_title :comment :article_link :issue_id :short_issue_id
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
      (keys response) => (contains [:first_name :last_name :user_id :timestamp :issue_id :short_issue_id :author_id
                                    :article_title :type :bill_id :bill_title :comment
                                    :article_link :article_img :emotional_response :event_id
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
      (keys response) => (contains [:first_name :last_name :user_id :timestamp :issue_id :short_issue_id :author_id
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
      results => []))

  (fact "Given an existing issue, When the user updates the comment body, Then verify updated body is in response."
    (let [{body :body} (pav-req :put "/user" test-user)
          {token :token} (ch/parse-string body true)
          ;;publish issue
          {body :body} (pav-req :put "/user/issue" token {:comment "Comment Body goes here" :bill_id "hr2-114"})
          {issue_id :issue_id} (ch/parse-string body true)
          ;;update issue
          {status :status body :body} (pav-req :post (str "/user/issue/" issue_id) token {:comment "Updated comment body"})
          response (ch/parse-string body true)]
      status => 201
      (:comment response) => "Updated comment body"
      (keys response) => (contains [:user_id :first_name :last_name :timestamp
                                    :bill_id :bill_title :comment :issue_id :short_issue_id :emotional_response
                                    :positive_responses :negative_responses :neutral_responses] :in-any-order)
      ;; make sure all keys has values
      (some nil? (vals response)) => nil))

  (fact "Given an existing issue, When another user tries to update the issue, Then return 401 error"
    (let [{body :body} (pav-req :put "/user" test-user)
          {token :token} (ch/parse-string body true)
          ;;publish issue
          {body :body} (pav-req :put "/user/issue" token {:comment "Comment Body goes here" :bill_id "hr2-114"})
          {issue_id :issue_id} (ch/parse-string body true)
          ;;update issue with by user without permission
          {body :body} (pav-req :put "/user" follower)
          {token :token} (ch/parse-string body true)
          {status :status} (pav-req :post (str "/user/issue/" issue_id) token {:comment "Updated comment body"})]
      status => 401))

  (fact "Update an existing issue, When the payload contains a new article_link, Then verify correct open graph data"
    (let [{body :body} (pav-req :put "/user" test-user)
          {token :token} (ch/parse-string body true)
          {body :body} (pav-req :put "/user/issue" token
                                        {:bill_id "hr2-114"
                                         :comment "Comment Body goes here"
                                         :article_link "http://time.com/3319278/isis-isil-twitter/"})
          {issue_id :issue_id} (ch/parse-string body true)
          {status :status body :body} (pav-req :post (str "/user/issue/" issue_id) token
                                        {:article_link "http://time.com/4225033/george-w-bush-counter-punches-donald-trump-at-jeb-rally/"})
          response (ch/parse-string body true)]
      status => 201
      response => (contains {:article_link "http://time.com/4225033/george-w-bush-counter-punches-donald-trump-at-jeb-rally/"
                             :article_img anything
                             :article_title "George W. Bush Counterpunches Donald Trump at Jeb! Rally"})
      (keys response) => (contains [:user_id :first_name :last_name
                                    :bill_id :bill_title :comment :article_link :issue_id :short_issue_id
                                    :article_title :article_img :emotional_response
                                    :positive_responses :negative_responses :neutral_responses] :in-any-order)
      ;; make sure all keys has values
      (some nil? (vals response)) => nil))

  (fact "Given an existing issue, When issue is updated, Then verify changes are reflected in followers feed
         and feed item has correct user meta data."
    (let [{body :body} (pav-req :put "/user" follower)
          {follower_token :token} (ch/parse-string body true)
          {body :body} (pav-req :put "/user" test-user)
          {token :token user_id :user_id} (ch/parse-string body true)
          ;;follow user
          _ (pav-req :put (str "/user/follow") follower_token {:user_id user_id})
          ;;publish issue
          {body :body} (pav-req :put "/user/issue" token
                         {:bill_id "hr2-114"
                          :comment "Comment Body goes here"
                          :article_link "http://time.com/3319278/isis-isil-twitter/"})
          {issue_id :issue_id} (ch/parse-string body true)
          ;;update issue
          _ (pav-req :post (str "/user/issue/" issue_id) token
              {:article_link "http://time.com/4225033/george-w-bush-counter-punches-donald-trump-at-jeb-rally/"})
          ;;extract issue from followers feed
          {status :status body :body} (pav-req :get "/user/feed" follower_token {})
          response (first (:results (ch/parse-string body true)))]
      status => 200
      response => (contains {:article_link "http://time.com/4225033/george-w-bush-counter-punches-donald-trump-at-jeb-rally/"
                             :article_img anything
                             :article_title "George W. Bush Counterpunches Donald Trump at Jeb! Rally"
                             :first_name (:first_name test-user)
                             :last_name (:last_name test-user)
                             :user_id user_id})
      ;; make sure all keys has values
      (some nil? (vals response)) => nil))

  (fact "Given a new issue, Then retrieve single issue by id"
    (let [{body :body} (pav-req :put "/user" test-user)
          {token :token} (ch/parse-string body true)
          {body :body} (pav-req :put "/user/issue" token
                         {:bill_id "hr2-114" :comment "Comment Body goes here"
                          :article_link "http://time.com/3319278/isis-isil-twitter/"})
          {issue_id :issue_id} (ch/parse-string body true)
          {status :status body :body} (pav-req :get (str "/user/issue/" issue_id))
          response (ch/parse-string body true)]
      status => 200
      (keys response) => (contains [:user_id :first_name :last_name :short_issue_id
                                    :bill_id :bill_title :comment :article_link :issue_id
                                    :article_title :article_img :emotional_response
                                    :positive_responses :negative_responses :neutral_responses] :in-any-order)
      (some nil? (vals response)) => nil))

  (fact "Given a new issue, Then retrieve single issue by short_issue_id"
    (let [{body :body} (pav-req :put "/user" test-user)
          {token :token} (ch/parse-string body true)
          {body :body} (pav-req :put "/user/issue" token
                         {:bill_id "hr2-114" :comment "Comment Body goes here"
                          :article_link "http://time.com/3319278/isis-isil-twitter/"})
          {short_issue_id :short_issue_id} (ch/parse-string body true)
          {status :status body :body} (pav-req :get (str "/user/issue/" short_issue_id))
          response (ch/parse-string body true)]
      status => 200
      (keys response) => (contains [:user_id :first_name :last_name :short_issue_id
                                    :bill_id :bill_title :comment :article_link :issue_id
                                    :article_title :article_img :emotional_response
                                    :positive_responses :negative_responses :neutral_responses] :in-any-order)
      (some nil? (vals response)) => nil))

  (fact "Given a new issue, When authors token is present, Then retrieve single issue by id with emotional response data"
    (let [{body :body} (pav-req :put "/user" test-user)
          {token :token author_id :user_id} (ch/parse-string body true)
          {body :body} (pav-req :put "/user/issue" token
                         {:bill_id "hr2-114" :comment "Comment Body goes here"
                          :article_link "http://time.com/3319278/isis-isil-twitter/"})
          {issue_id :issue_id} (ch/parse-string body true)
          {status :status body :body} (pav-req :get (str "/user/issue/" issue_id) token {})
          response (ch/parse-string body true)]
      status => 200
      (:user_id response) => author_id
      (keys response) => (contains [:user_id :first_name :last_name
                                    :bill_id :bill_title :comment :article_link :issue_id :short_issue_id
                                    :article_title :article_img :emotional_response
                                    :positive_responses :negative_responses :neutral_responses] :in-any-order)
      (some nil? (vals response)) => nil))

  (fact "Given a new issue, When an alternative user token is present, Then retrieve single issue by id with emotional response data"
    (let [{body :body} (pav-req :put "/user" test-user)
          {token :token author_id :user_id} (ch/parse-string body true)
          {body :body} (pav-req :put "/user/issue" token
                         {:bill_id "hr2-114" :comment "Comment Body goes here"
                          :article_link "http://time.com/3319278/isis-isil-twitter/"})
          {issue_id :issue_id} (ch/parse-string body true)
          {body :body} (pav-req :put "/user/facebook" test-fb-user)
          {nonauthor_token :token} (ch/parse-string body true)
          {status :status body :body} (pav-req :get (str "/user/issue/" issue_id) nonauthor_token {})
          response (ch/parse-string body true)]
      status => 200
      (:user_id response) => author_id
      (keys response) => (contains [:user_id :first_name :last_name
                                    :bill_id :bill_title :comment :article_link :issue_id :short_issue_id
                                    :article_title :article_img :emotional_response
                                    :positive_responses :negative_responses :neutral_responses] :in-any-order)
      (some nil? (vals response)) => nil))

  (fact "Try retrieving issue that doesn't exist, Then return 404"
    (let [{status :status body :body} (pav-req :get "/user/issue/94873662-5d2d-497a-9d30-7c185b042abdd")
          response (ch/parse-string body true)]
      status => 404
      (keys response) => [:error_message]))

  (fact "Given a new issue, Then verify the issue appears in all users feeds."
    (let [{body :body} (pav-req :put "/user" follower)
          {user1_token :token} (ch/parse-string body true)
          {body :body} (pav-req :put "/user" test-user)
          {user2_token :token} (ch/parse-string body true)
          ;;publish issue
          _(pav-req :put "/user/issue" user2_token {:comment "Comment Body goes here"})
          ;;retrieve followers feed
          _ (Thread/sleep 2000)
          {status :status body :body} (pav-req :get "/user/feed" user1_token {})
          response (first (:results (ch/parse-string body true)))]
      status => 200
      (some nil? (vals response)) => nil
      (keys response) => (contains [:first_name :last_name :user_id :timestamp :issue_id :short_issue_id :author_id
                                    :comment :emotional_response :type :event_id
                                    :positive_responses :negative_responses :neutral_responses] :in-any-order))))