(ns com.pav.user.api.test.bill.bill-comment-api-test
  (:use midje.sweet)
  (:require [com.pav.user.api.test.utils.utils :as utils :refer [pav-req]]
            [cheshire.core :as ch]
            [buddy.sign.jws :as jws]
            [buddy.sign.util :as u]
            [buddy.core.keys :as ks]
            [clj-time.core :as t]
            [environ.core :refer [env]]))

(defn- pkey []
  (ks/private-key (:auth-priv-key env) (:auth-priv-key-pwd env)))

(defn create-auth-token [user]
  (jws/sign user (pkey)
    {:alg :rs256 :exp (-> (t/plus (t/now) (t/days 30)) (u/to-timestamp))}))

(def test-token (create-auth-token {:user_id "12345" :first_name "John" :last_name "Rambo" :img_url "https://img.com"}))
(def test-comment {:bill_id "hr2-114" :body    "comment goes here!!"})
(def expected-comment-response {:bill_id "hr2-114" :author  "12345" :author_img_url "https://img.com" :author_first_name "John"
                                :author_last_name "Rambo" :body "comment goes here!!" :score 0 :parent_id nil :has_children false})

(against-background [(before :facts (do (utils/flush-es-indexes)
                                        (utils/bootstrap-bills-and-metadata)
                                        (utils/flush-redis)
                                        (utils/flush-dynamo-tables)))]

  (facts "Test cases covering the bill comments API Endpoints."

    (fact "Associate a comment with a given bill"
      (let [{status :status body :body} (pav-req :put "/bills/comments" test-token test-comment)
            body (ch/parse-string body true)]
        status => 201
        body => (contains expected-comment-response)
        (keys body) => (contains [:bill_id :author :author_first_name :author_last_name :body :score :comment_id :id
                                  :timestamp :parent_id :has_children] :in-any-order)))

    (fact "Try creating a comment without a bill_id, should throw 400 HTTP Status code"
      (let [{status :status} (pav-req :put "/bills/comments" test-token {:body "comment goes here!!"})]
        status => 400))

    (fact "Associating a comment with an invalid Authentication token, should return 401"
      (let [{status :status} (pav-req :put "/bills/comments" "token" test-comment)]
        status => 401))

    (fact "Retrieve top comments for a given bill"
      (let [_ (pav-req :put "/bills/comments" test-token test-comment)
            _ (pav-req :put "/bills/comments" (create-auth-token {:user_id "2468"}) test-comment)
            {status :status body :body} (pav-req :get "/bills/hr2-114/topcomments" (create-auth-token {:user_id "5678"}) {})
            response (ch/parse-string body true)]
        status => 200
        (keys response) => (contains [:for-comment :against-comment])))

    (fact "Retrieve top comments for a given bill, When request has no Authentication Token, Then Retrieve top comments"
      (let [_ (pav-req :put "/bills/comments" test-token test-comment)
            _ (pav-req :put "/bills/comments" (create-auth-token {:user_id "2468"}) test-comment)
            {status :status body :body} (pav-req :get "/bills/hr2-114/topcomments")
            response (ch/parse-string body true)]
        status => 200
        (keys response) => (contains [:for-comment :against-comment])))

    (fact "Reply to existing comment"
      (let [{body :body} (pav-req :put "/bills/comments" test-token test-comment)
            parent-comment-id (:comment_id (ch/parse-string body true))
            {status :status reply-response-body :body} (pav-req :put (str "/comments/" parent-comment-id "/reply")
                                                         (create-auth-token {:user_id "2468" :img_url "https://img.com"
                                                                             :first_name "Peter" :last_name "Pan"})
                                                         {:bill_id "hr2-114"
                                                          :body    "reply to user1 comment"})
            reply-response-body (ch/parse-string reply-response-body true)
            ]
        status => 201
        reply-response-body => (contains {:bill_id "hr2-114"
                                          :author "2468"
                                          :author_first_name "Peter"
                                          :author_last_name "Pan"
                                          :author_img_url "https://img.com"
                                          :body "reply to user1 comment"
                                          :has_children false})
        (:parent_id reply-response-body) => parent-comment-id))

    (fact "Try replying to a comment without a body, should throw 400 HTTP Status code"
      (let [{status :status} (pav-req :put "/comments/comment:1/reply" test-token {:bill_id "hr2-114"})]
        status => 400))

    (fact "Try replying to comment with invalid Authentication Token"
      (let [{status :status} (pav-req :put (str "/comments/comment:1/reply") "token" test-comment)]
        status => 401))

    (fact "Retrieve comments associated with a given bill"
      (let [_ (pav-req :put "/bills/comments" test-token test-comment)
            {status :status body :body} (pav-req :get "/bills/hr2-114/comments" test-token {})
            comments (ch/parse-string body true)]
        status => 200
        (:total comments) => 1
        (first (:comments comments)) => (contains (assoc expected-comment-response
                                                    :replies [] :liked false :disliked false))))

    (fact "Retrieving comments associated with a given bill, When missing Authentication token, Then return comment without
        user meta data."
      (let [_ (pav-req :put "/bills/comments" test-token test-comment)
            {status :status body :body} (pav-req :get "/bills/hr2-114/comments")
            comments (ch/parse-string body true)]
        status => 200
        (:total comments) => 1
        (first (:comments comments)) => (contains (assoc expected-comment-response
                                                    :replies [] :liked false :disliked false))))

    (fact "When retrieving comment for a Bill, if there are no comments between an empty list"
      (let [{status :status body :body} (pav-req :get "/bills/hr2-114/comments" test-token {})
            comments (ch/parse-string body true)]
        status => 200
        (:total comments) => 0
        (:comments comments) => []))

    (fact "Retrieve nested comments associated with a given bill"
      (let [{parent-comment-response :body} (pav-req :put "/bills/comments" test-token test-comment)
            parent-comment-id (:comment_id (ch/parse-string parent-comment-response true))
            _ (pav-req :put (str "/comments/" parent-comment-id "/reply") (create-auth-token {:user_id "12345"}) test-comment)
            _ (pav-req :put (str "/comments/" parent-comment-id "/reply") (create-auth-token {:user_id "123456"}) test-comment)
            {status :status body :body} (pav-req :get "/bills/hr2-114/comments" (create-auth-token {:user_id "1234"}) {})
            comments (ch/parse-string body true)]
        status => 200
        (:total comments) => 1
        (count (get-in (first (:comments comments)) [:replies])) => 2))

    (fact "Like a comment"
      (let [{body :body} (pav-req :put "/bills/comments" test-token test-comment)
            comment-body (ch/parse-string body true)
            {status :status} (pav-req :post (str "/comments/" (:comment_id comment-body) "/like") test-token {:bill_id "hr2-114"})
            {liked-comment :body} (pav-req :get "/bills/hr2-114/comments" test-token {})
            {comments :comments} (ch/parse-string liked-comment true)]
        status => 201
        (first comments) => (contains {:liked true :disliked false})))

    (fact "Revoke a Liked comment"
      (let [{body :body} (pav-req :put "/bills/comments" test-token test-comment)
            comment-body (ch/parse-string body true)
            _ (pav-req :post (str "/comments/" (:comment_id comment-body) "/like") test-token {:bill_id "hr2-114"})
            {delete-status :status} (pav-req :delete (str "/comments/" (:comment_id comment-body) "/like") test-token {:bill_id "hr2-114"})
            {liked-comment :body} (pav-req :get "/bills/hr2-114/comments" test-token {})
            {comments :comments} (ch/parse-string liked-comment true)]
        delete-status => 204
        (first comments) => (contains {:liked false :disliked false})))

    (fact "Try Liking a comment without a bill_id, should throw 400 HTTP Status code"
      (let [{status :status} (pav-req :post (str "/comments/1001/like") test-token {})]
        status => 400))

    (fact "Try liking a comment without an invalid token"
      (let [{status :status} (pav-req :post (str "/comments/comment:1/like") "token" {:bill_id "hr2-114"})]
        status => 401))

    (fact "Dislike a comment"
      (let [{body :body} (pav-req :put "/bills/comments" test-token test-comment)
            comment-body (ch/parse-string body true)
            {status :status} (pav-req :post (str "/comments/" (:comment_id comment-body) "/dislike") test-token {:bill_id "hr2-114"})
            {disliked-comment :body} (pav-req :get "/bills/hr2-114/comments" test-token {})
            {comments :comments} (ch/parse-string disliked-comment true)]
        status => 201
        (first comments) => (contains {:liked false :disliked true})))

    (fact "Revoke a disliked comment"
      (let [{body :body} (pav-req :put "/bills/comments" test-token test-comment)
            comment-body (ch/parse-string body true)
            _ (pav-req :post (str "/comments/" (:comment_id comment-body) "/dislike") test-token {:bill_id "hr2-114"})
            {delete-status :status} (pav-req :delete (str "/comments/" (:comment_id comment-body) "/dislike") test-token {:bill_id "hr2-114"})
            {disliked-comment :body} (pav-req :get "/bills/hr2-114/comments" test-token {})
            {comments :comments} (ch/parse-string disliked-comment true)]
        delete-status => 204
        (first comments) => (contains {:liked false :disliked false})))

    (fact "Try Disliking a comment without a bill_id, should throw 400 HTTP Status code"
      (let [{status :status} (pav-req :post (str "/comments/1001/dislike") test-token {})]
        status => 400))

    (fact "Try disliking a comment without an invalid token"
      (let [{status :status} (pav-req :post (str "/comments/comment:1/dislike") "token" {:bill_id "hr2-114"})]
        status => 401))

    (fact "Like a comment reply"
      (let [{body :body} (pav-req :put "/bills/comments" test-token test-comment)
            parent-comment-id (:comment_id (ch/parse-string body true))
            first-reply-response (ch/parse-string (:body (pav-req :put (str "/comments/" parent-comment-id "/reply") test-token test-comment)) true)
            _ (ch/parse-string (:body (pav-req :put (str "/comments/" parent-comment-id "/reply") test-token test-comment)) true)
            _ (pav-req :post (str "/comments/" (:comment_id first-reply-response) "/like") test-token {:bill_id "hr2-114"})
            response (pav-req :get "/bills/hr2-114/comments" test-token {})
            comments (ch/parse-string (:body response) true)]
        (:status response) => 200
        (:total comments) => 1
        (:score (first (get-in (first (:comments comments)) [:replies]))) => 1
        (:liked (first (get-in (first (:comments comments)) [:replies]))) => true
        (:disliked (first (get-in (first (:comments comments)) [:replies]))) => false))

    (fact "Dislike a comment reply"
      (let [{body :body} (pav-req :put "/bills/comments" test-token test-comment)
            parent-comment-id (:comment_id (ch/parse-string body true))
            first-reply-response (ch/parse-string (:body (pav-req :put (str "/comments/" parent-comment-id "/reply") test-token test-comment)) true)
            _ (ch/parse-string (:body (pav-req :put (str "/comments/" parent-comment-id "/reply") test-token test-comment)) true)
            _ (pav-req :post (str "/comments/" (:comment_id first-reply-response) "/dislike") test-token {:bill_id "hr2-114"})
            {status :status body :body} (pav-req :get "/bills/hr2-114/comments" test-token {})
            comments (ch/parse-string body true)]
        status => 200
        (:total comments) => 1
        (:score (second (get-in (first (:comments comments)) [:replies]))) => -1
        (:disliked (second (get-in (first (:comments comments)) [:replies]))) => true
        (:liked (second (get-in (first (:comments comments)) [:replies]))) => false))

    (fact "Create two comments, When retrieving them by most recent, Then ensure correct ordering"
      (let [_ (pav-req :put "/bills/comments" test-token test-comment)
            {body :body} (pav-req :put "/bills/comments" test-token test-comment)
            {comment2ID :comment_id} (ch/parse-string body true)
            {status :status body :body} (pav-req :get "/bills/hr2-114/comments?sort-by=latest")
            response (ch/parse-string body true)]
        status => 200
        (:total response) => 2
        (:comment_id (first (:comments response))) => comment2ID))

    (fact "Create two comments, like a comment and retrieve them by highest score, Then ensure correct ordering"
      (let [_ (pav-req :put "/bills/comments" test-token test-comment)
            {body :body} (pav-req :put "/bills/comments" test-token test-comment)
            {comment2ID :comment_id} (ch/parse-string body true)
            _ (pav-req :post (str "/comments/" comment2ID "/like") test-token {:bill_id "hr2-114"})
            {status :status body :body} (pav-req :get "/bills/hr2-114/comments?sort-by=highest-score")
            response (ch/parse-string body true)
            first-comment (first (:comments response))]
        status => 200
        (:total response) => 2
        (:comment_id first-comment) => comment2ID
        (:score first-comment) => 1))

    (fact "Create two comments, like a comment, Then ensure by default they are sorted by highest score."
      (let [_ (pav-req :put "/bills/comments" test-token test-comment)
            {body :body} (pav-req :put "/bills/comments" test-token test-comment)
            {comment2ID :comment_id} (ch/parse-string body true)
            _ (pav-req :post (str "/comments/" comment2ID "/like") test-token {:bill_id "hr2-114"})
            {status :status body :body} (pav-req :get "/bills/hr2-114/comments")
            response (ch/parse-string body true)
            first-comment (first (:comments response))]
        status => 200
        (:total response) => 2
        (:comment_id first-comment) => comment2ID
        (:score first-comment) => 1))))
