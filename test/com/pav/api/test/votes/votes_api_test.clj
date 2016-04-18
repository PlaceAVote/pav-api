(ns com.pav.api.test.votes.votes-api-test
  (:use midje.sweet)
  (:require [com.pav.api.test.utils.utils :as u :refer [pav-req
                                                        new-pav-user]]))

(against-background [(before :contents (do (u/flush-dynamo-tables) (u/flush-redis)))]
  (facts "Test cases to cover votes API."

    (fact "Given a vote request, process vote and return a 201 HTTP response"
      (let [{body :body} (pav-req :put "/user" (new-pav-user))
            {token :token} body
            new-vote {:bill_id "hr2-114" :vote true}
            {status :status body :body} (pav-req :put "/vote" token new-vote)]
        status => 201
        body => {:message "Vote created successfully"}))

    (fact "Given a vote request, with an invalid token, return 401 HTTP response"
      (let [{status :status} (pav-req :put "/vote" "token" {:bill_id "hr2-114" :vote true})]
        status => 401))

    (fact "Validate payload for missing bill-id, should return 400 HTTP response"
      (let [{body :body} (pav-req :put "/user" (new-pav-user))
            {token :token} body
            new-vote {:vote true}
            {status :status} (pav-req :put "/vote" token new-vote)]
        status => 400))

    (fact "Validate payload for missing vote, should return 400 HTTP response"
      (let [{body :body} (pav-req :put "/user" (new-pav-user))
            {token :token} body
            new-vote {:bill_id "hr2-114"}
            {status :status} (pav-req :put "/vote" token new-vote)]
        status => 400))

    (fact "Given a vote request, when user has already voted on bill then return 409 HTTP response"
      (let [{body :body} (pav-req :put "/user" (new-pav-user))
            {token :token} body
            new-vote {:bill_id "hr2-114" :vote true}
            _ (pav-req :put "/vote" token new-vote)
            {status :status body :body} (pav-req :put "/vote" token new-vote)]
        status => 409
        body => {:error "User has already voted on this issue"}))

    (fact "Retrieve vote count for a given bill"
      (u/flush-dynamo-tables)
      (let [{body :body} (pav-req :put "/user" (new-pav-user))
            {token :token} body
            new-vote {:bill_id "hr2-114" :vote true}
            _ (pav-req :put "/vote" token new-vote)
            {status :status count :body} (pav-req :get "/vote/count?bill-id=hr2-114" token {})]
        status => 200
        count => {:bill_id "hr2-114" :yes-count 1 :no-count 0}))

    (fact "Retrieving vote count, When request has no Authentication Token, Then return count."
      (u/flush-dynamo-tables)
      (let [{body :body} (pav-req :put "/user" (new-pav-user))
            {token :token} body
            new-vote {:bill_id "hr2-114" :vote true}
            _ (pav-req :put "/vote" token new-vote)
            {status :status count :body} (pav-req :get "/vote/count?bill-id=hr2-114")]
        status => 200
        count => {:bill_id "hr2-114" :yes-count 1 :no-count 0}))

    (fact "Retrieve vote records for a given bill"
      (let [{body :body} (pav-req :put "/user" (new-pav-user))
            {token :token} body
            new-vote {:bill_id "hr2-114" :vote true}
            _ (pav-req :put "/vote" token new-vote)
            {status :status body :body} (pav-req :get "/vote/bill/hr2-114" token {})
            response (first body)]
        status => 200
        (keys response) => (contains [:bill_id :vote :timestamp :created_at] :in-any-order)
        response => (just {:bill_id "hr2-114" :vote true :timestamp anything :created_at anything})))

    (fact "Retrieving vote records with no Authentication Token, then return vote records for given bill"
      (let [{body :body} (pav-req :put "/user" (new-pav-user))
            {token :token} body
            new-vote {:bill_id "hr2-114" :vote true}
            _ (pav-req :put "/vote" token new-vote)
            {status :status body :body} (pav-req :get "/vote/bill/hr2-114")]
        status => 200
        (first body) => (just {:bill_id "hr2-114" :vote true :timestamp anything :created_at anything})))

    (fact "When retrieving vote records for a bill that doesn't exist, return empty list"
      (u/flush-dynamo-tables)
      (let [{body :body} (pav-req :get "/vote/bill/hr2-114")]
        body => []))

    (fact "Given new vote, Then verify that vote record appears in the users timeline, notifications"
      (let [{body :body} (pav-req :put "/user" (new-pav-user))
            {token :token} body
            new-vote {:bill_id "hr2-114" :vote true}
            _ (pav-req :put "/vote" token new-vote)
            _ (Thread/sleep 2000)
            {body :body} (pav-req :get "/user/me/timeline" token {})
            {timeline-events :results} body
            {body :body} (pav-req :get "/user/notifications" token {})
            {notifications :results} body]
        (keys (first timeline-events)) => (just [:bill_id :bill_title :type :event_id :user_id :timestamp :vote-id]
                                            :in-any-order)
        (keys (first notifications)) => (just [:bill_id :bill_title :type :notification_id :user_id :timestamp :vote-id
                                               :read] :in-any-order)))

    (fact "Given new vote, When the voter has followers, Then verify vote event appears in followers feed."
      (let [;; Create follower
            {body :body} (pav-req :put "/user" (new-pav-user {:email "random@placeavote.com"}))
            {follower-token :token} body
            ;; Create Voter
            {body :body} (pav-req :put "/user" (new-pav-user))
            {voter-token :token voter-id :user_id} body
            ;;Follow voter
            _ (pav-req :put (str "/user/follow") follower-token {:user_id voter-id})
            new-vote {:bill_id "hr2-114" :vote true}
            ;;Cast vote
            _ (pav-req :put "/vote" voter-token new-vote)
            ;; Retrieve Followers feed.
            _ (Thread/sleep 2000)
            {body :body} (pav-req :get "/user/feed" follower-token {})
            {feed-events :results} body]
        (keys (first feed-events)) => (just [:bill_id :bill_title :type :event_id :user_id :timestamp :vote-id :read
                                             :voter_id :voter_first_name :voter_last_name :voter_img_url]
                                            :in-any-order)))))