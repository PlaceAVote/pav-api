(ns com.pav.api.test.bill.bill-comment-api-test
  (:use midje.sweet)
  (:require [com.pav.api.test.utils.utils :as utils :refer [pav-req new-pav-user]]
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

(def test-comment {:bill_id "hr2-114" :body "comment goes here!!"})

(def expected-comment-response {:bill_id "hr2-114" :author  anything :author_img_url anything :author_first_name anything
                                :author_last_name anything :body "comment goes here!!" :score 0 :parent_id nil :has_children false})

(against-background [(before :contents (do (utils/flush-es-indexes)
                                           (utils/bootstrap-bills-and-metadata)
                                           (utils/flush-redis)
                                           (utils/flush-dynamo-tables)))]

  (facts "Test cases covering the bill comments API Endpoints."

    (fact "Associate a comment with a given bill"
      (let [token (-> (utils/create-user) create-auth-token)
            {status :status body :body} (pav-req :put "/bills/comments" token test-comment)]
        status => 201
        body => (contains expected-comment-response)
        (keys body) => (contains [:bill_id :author :author_first_name :author_last_name :body :score :comment_id :id
                                  :timestamp :parent_id :has_children :liked :disliked :replies] :in-any-order)))

    (fact "Update an existing comment and verify its new body"
      (let [token (-> (utils/create-user) create-auth-token)
            {body :body} (pav-req :put "/bills/comments" token test-comment)
            {comment_id :comment_id} body
            {status :status body :body} (pav-req :post (str "/comments/" comment_id) token {:body "I have been updated"})]
        status => 201
        (:body body) => "I have been updated"
        (keys body) => (contains [:bill_id :author :author_first_name :author_last_name
                                  :body :score :comment_id :id :timestamp :parent_id :has_children] :in-any-order)))

    (fact "Update an existing comment, When the token does not belong to the author, Then throw 401."
      (let [token (-> (utils/create-user) create-auth-token)
            {body :body} (pav-req :put "/bills/comments" token test-comment)
            {comment_id :comment_id} body
            {status :status} (pav-req :post (str "/comments/" comment_id)
                               (create-auth-token {:user_id "678910"}) {:body "I'm not allowed to update this comment"})]
        status => 401))

    (fact "Deleting an existing comment, Then verify the comment is marked as deleted"
      (let [token (-> (utils/create-user) create-auth-token)
            {body :body} (pav-req :put "/bills/comments" token test-comment)
            {comment_id :comment_id} body
            {status :status} (pav-req :delete (str "/comments/" comment_id) token {})]
        status => 204))

    (fact "Deleting an existing comment, When the token does not belong to the author, Then throw 401."
      (let [token (-> (utils/create-user) create-auth-token)
            {body :body} (pav-req :put "/bills/comments" token test-comment)
            {comment_id :comment_id} body
            {status :status} (pav-req :delete (str "/comments/" comment_id) (create-auth-token {:user_id "678910"}) {})]
        status => 401))

    (fact "Try creating a comment without a bill_id, should throw 400 HTTP Status code"
      (let [token (-> (utils/create-user) create-auth-token)
            {status :status} (pav-req :put "/bills/comments" token {:body "comment goes here!!"})]
        status => 400))

    (fact "Associating a comment with an invalid Authentication token, should return 401"
      (let [{status :status} (pav-req :put "/bills/comments" "token" test-comment)]
        status => 401))

    (fact "Retrieve top comments for a given bill"
      (let [token (-> (utils/create-user) create-auth-token)
            {status :status body :body} (pav-req :get "/bills/hr2-114/topcomments" token {})
            response body]
        status => 200
        (keys response) => (contains [:for-comment :against-comment])))

    (fact "Retrieve top comments for a given bill, When request has no Authentication Token, Then Retrieve top comments"
      (let [{status :status body :body} (pav-req :get "/bills/hr2-114/topcomments")
            response body]
        status => 200
        (keys response) => (contains [:for-comment :against-comment])))

    (fact "Reply to existing comment"
      (let [user (utils/create-user)
            token (create-auth-token user)
            {body :body} (pav-req :put "/bills/comments" token test-comment)
            parent-comment-id (:comment_id body)
            expected-reply {:bill_id "hr2-114" :author anything :author_first_name (:first_name user)
                            :author_last_name (:last_name user) :author_img_url anything :body "reply to user1 comment"
                            :has_children false :parent_id parent-comment-id}
            {status :status reply-response-body :body} (pav-req :put (str "/comments/" parent-comment-id "/reply") token
                                                         {:bill_id "hr2-114" :body "reply to user1 comment"})
            reply-response-body reply-response-body]
        status => 201
        reply-response-body => (contains expected-reply)))

    (fact "Try replying to a comment without a body, should throw 400 HTTP Status code"
      (let [token (-> (utils/create-user) create-auth-token)
            {status :status} (pav-req :put "/comments/comment:1/reply" token {:bill_id "hr2-114"})]
        status => 400))

    (fact "Try replying to comment with invalid Authentication Token"
      (let [{status :status} (pav-req :put (str "/comments/comment:1/reply") "token" test-comment)]
        status => 401))

    (fact "Retrieve comments associated with a given bill"
      (utils/flush-dynamo-tables)
      (let [token (-> (utils/create-user) create-auth-token)
            _ (pav-req :put "/bills/comments" token test-comment)
            {status :status body :body} (pav-req :get "/bills/hr2-114/comments" token {})
            comments body]
        status => 200
        (:total comments) => 1
        (first (:comments comments)) => (contains (assoc expected-comment-response
                                                    :replies [] :liked false :disliked false))))

    (fact "Retrieving comments associated with a given bill, When missing Authentication token, Then return comment without
        user meta data."
      (utils/flush-dynamo-tables)
      (let [token (-> (utils/create-user) create-auth-token)
            _ (pav-req :put "/bills/comments" token test-comment)
            {status :status body :body} (pav-req :get "/bills/hr2-114/comments")
            comments body]
        status => 200
        (:total comments) => 1
        (first (:comments comments)) => (contains (assoc expected-comment-response
                                                    :replies [] :liked false :disliked false))))

    (fact "When retrieving comment for a Bill, if there are no comments between an empty list"
      (utils/flush-dynamo-tables)
      (let [{status :status body :body} (pav-req :get "/bills/hr2-114/comments")
            comments body]
        status => 200
        (:total comments) => 0
        (:comments comments) => []))

    (fact "Retrieve nested comments associated with a given bill"
      (let [token (-> (utils/create-user) create-auth-token)
            {parent-comment-response :body} (pav-req :put "/bills/comments" token test-comment)
            parent-comment-id (:comment_id parent-comment-response)
            _ (pav-req :put (str "/comments/" parent-comment-id "/reply") token test-comment)
            _ (pav-req :put (str "/comments/" parent-comment-id "/reply") token test-comment)
            {status :status body :body} (pav-req :get "/bills/hr2-114/comments")
            comments body]
        status => 200
        (:total comments) => 1
        (count (get-in (first (:comments comments)) [:replies])) => 2))

    (fact "Like a comment"
      (utils/flush-dynamo-tables)
      (let [token (-> (utils/create-user) create-auth-token)
            {body :body} (pav-req :put "/bills/comments" token test-comment)
            comment-body body
            {status :status} (pav-req :post (str "/comments/" (:comment_id comment-body) "/like") token {:bill_id "hr2-114"})
            {liked-comment :body} (pav-req :get "/bills/hr2-114/comments" token {})
            {comments :comments} liked-comment]
        status => 201
        (first comments) => (contains {:liked true :disliked false})))

    (fact "Revoke a Liked comment"
      (utils/flush-dynamo-tables)
      (let [token (-> (utils/create-user) create-auth-token)
            {body :body} (pav-req :put "/bills/comments" token test-comment)
            comment-body body
            _ (pav-req :post (str "/comments/" (:comment_id comment-body) "/like") token {:bill_id "hr2-114"})
            {delete-status :status} (pav-req :delete (str "/comments/" (:comment_id comment-body) "/like") token {:bill_id "hr2-114"})
            {liked-comment :body} (pav-req :get "/bills/hr2-114/comments" token {})
            {comments :comments} liked-comment]
        delete-status => 204
        (first comments) => (contains {:liked false :disliked false})))

    (fact "Try Liking a comment without a bill_id, should throw 400 HTTP Status code"
      (let [token (-> (utils/create-user) create-auth-token)
            {status :status} (pav-req :post (str "/comments/1001/like") token {})]
        status => 400))

    (fact "Try liking a comment without an invalid token"
      (let [{status :status} (pav-req :post (str "/comments/comment:1/like") "token" {:bill_id "hr2-114"})]
        status => 401))

    (fact "Dislike a comment"
      (utils/flush-dynamo-tables)
      (let [token (-> (utils/create-user) create-auth-token)
            {body :body} (pav-req :put "/bills/comments" token test-comment)
            comment-body body
            {status :status} (pav-req :post (str "/comments/" (:comment_id comment-body) "/dislike") token {:bill_id "hr2-114"})
            {disliked-comment :body} (pav-req :get "/bills/hr2-114/comments" token {})
            {comments :comments} disliked-comment]
        status => 201
        (first comments) => (contains {:liked false :disliked true})))

    (fact "Revoke a disliked comment"
      (utils/flush-dynamo-tables)
      (let [token (-> (utils/create-user) create-auth-token)
            {body :body} (pav-req :put "/bills/comments" token test-comment)
            comment-body body
            _ (pav-req :post (str "/comments/" (:comment_id comment-body) "/dislike") token {:bill_id "hr2-114"})
            {delete-status :status} (pav-req :delete (str "/comments/" (:comment_id comment-body) "/dislike") token {:bill_id "hr2-114"})
            {disliked-comment :body} (pav-req :get "/bills/hr2-114/comments" token {})
            {comments :comments} disliked-comment]
        delete-status => 204
        (first comments) => (contains {:liked false :disliked false})))

    (fact "Try Disliking a comment without a bill_id, should throw 400 HTTP Status code"
      (let [token (-> (utils/create-user) create-auth-token)
            {status :status} (pav-req :post (str "/comments/1001/dislike") token {})]
        status => 400))

    (fact "Try disliking a comment without an invalid token"
      (let [{status :status} (pav-req :post (str "/comments/comment:1/dislike") "token" {:bill_id "hr2-114"})]
        status => 401))

    (fact "Like a comment reply"
      (utils/flush-dynamo-tables)
      (let [token (-> (utils/create-user) create-auth-token)
            {body :body} (pav-req :put "/bills/comments" token test-comment)
            parent-comment-id (:comment_id body)
            first-reply-response (:body (pav-req :put (str "/comments/" parent-comment-id "/reply") token test-comment))
            _ (pav-req :put (str "/comments/" parent-comment-id "/reply") token test-comment)
            _ (pav-req :post (str "/comments/" (:comment_id first-reply-response) "/like") token {:bill_id "hr2-114"})
            {status :status body :body} (pav-req :get "/bills/hr2-114/comments" token {})
            comments body]
        status => 200
        (:total comments) => 1
        (:score (first (get-in (first (:comments comments)) [:replies]))) => 1
        (:liked (first (get-in (first (:comments comments)) [:replies]))) => true
        (:disliked (first (get-in (first (:comments comments)) [:replies]))) => false))

    (fact "Dislike a comment reply"
      (utils/flush-dynamo-tables)
      (let [token (-> (utils/create-user) create-auth-token)
            {body :body} (pav-req :put "/bills/comments" token test-comment)
            parent-comment-id (:comment_id body)
            first-reply-response (:body (pav-req :put (str "/comments/" parent-comment-id "/reply") token test-comment))
            _ (pav-req :put (str "/comments/" parent-comment-id "/reply") token test-comment)
            _ (pav-req :post (str "/comments/" (:comment_id first-reply-response) "/dislike") token {:bill_id "hr2-114"})
            {status :status body :body} (pav-req :get "/bills/hr2-114/comments" token {})
            comments body]
        status => 200
        (:total comments) => 1
        (:score (second (get-in (first (:comments comments)) [:replies]))) => -1
        (:disliked (second (get-in (first (:comments comments)) [:replies]))) => true
        (:liked (second (get-in (first (:comments comments)) [:replies]))) => false))

    (fact "Create two comments, When retrieving them by most recent, Then ensure correct ordering"
      (utils/flush-dynamo-tables)
      (let [token (-> (utils/create-user) create-auth-token)
            _ (pav-req :put "/bills/comments" token test-comment)
            {body :body} (pav-req :put "/bills/comments" token test-comment)
            {comment2ID :comment_id} body
            {status :status body :body} (pav-req :get "/bills/hr2-114/comments?sort-by=latest")
            response body]
        status => 200
        (:total response) => 2
        (:comment_id (first (:comments response))) => comment2ID))

    (fact "Create two comments, like a comment and retrieve them by highest score, Then ensure correct ordering"
      (utils/flush-dynamo-tables)
      (let [token (-> (utils/create-user) create-auth-token)
            _ (pav-req :put "/bills/comments" token test-comment)
            {body :body} (pav-req :put "/bills/comments" token test-comment)
            {comment2ID :comment_id} body
            _ (pav-req :post (str "/comments/" comment2ID "/like") token {:bill_id "hr2-114"})
            {status :status body :body} (pav-req :get "/bills/hr2-114/comments?sort-by=highest-score")
            response body
            first-comment (first (:comments response))]
        status => 200
        (:total response) => 2
        (:comment_id first-comment) => comment2ID
        (:score first-comment) => 1))

    (fact "Create two comments, like a comment, Then ensure by default they are sorted by highest score."
      (utils/flush-dynamo-tables)
      (let [token (-> (utils/create-user) create-auth-token)
            _ (pav-req :put "/bills/comments" token test-comment)
            {body :body} (pav-req :put "/bills/comments" token test-comment)
            {comment2ID :comment_id} body
            _ (pav-req :post (str "/comments/" comment2ID "/like") token {:bill_id "hr2-114"})
            {status :status body :body} (pav-req :get "/bills/hr2-114/comments")
            response body
            first-comment (first (:comments response))]
        status => 200
        (:total response) => 2
        (:comment_id first-comment) => comment2ID
        (:score first-comment) => 1))

    (fact "Create a comment, When the author has followers, Then verify the follower has the new comment in there
          Newsfeed and the comment author has an event in there Timeline"
      (let [;;Create Follower
            {follower :body} (pav-req :put "/user" (new-pav-user))
            {follower-token :token} follower
            ;;Create Author
            {author :body} (pav-req :put "/user" (new-pav-user))
            {author_user_id :user_id author_token :token} author
            ;;Follow author
            _ (pav-req :put (str "/user/follow") follower-token {:user_id author_user_id})
            ;;Author Comment
            _ (pav-req :put "/bills/comments" author_token test-comment)
            _ (Thread/sleep 2000)
            {body :body} (pav-req :get "/user/me/timeline" author_token {})
            {timeline-events :results} body
            {body :body} (pav-req :get "/user/feed" follower-token {})
            {newsfeed-events :results} body]
        ;;check timeline event has correct author id
        (get-in (first timeline-events) [:author]) => author_user_id
        (keys (first timeline-events)) => (just [:event_id :user_id :bill_id :comment_id :type :bill_title
                                                 :author :author_img_url :author_first_name :author_last_name
                                                 :disliked :liked :timestamp :body :score] :in-any-order)
        ;;check newsfeed event has correct author id
        (get-in (first newsfeed-events) [:author]) => author_user_id
        (keys (first newsfeed-events)) => (just [:event_id :user_id :bill_id :comment_id :type :bill_title :read
                                                 :author :author_img_url :author_first_name :author_last_name
                                                 :disliked :liked :timestamp :body :score] :in-any-order)))

    (fact "Given a comment, When another user likes the comment, Then verify its in the users timeline."
      (let [;;Create Scorer
            {liker :body} (pav-req :put "/user" (new-pav-user))
            {liker_user_id :user_id liker-token :token} liker
            ;;Create Author
            {author :body} (pav-req :put "/user" (new-pav-user))
            {author_user_id :user_id author_token :token} author
            {body :body} (pav-req :put "/bills/comments" author_token test-comment)
            {comment_id :comment_id} body
            _ (pav-req :post (str "/comments/" comment_id "/like") liker-token {:bill_id "hr2-114"})
            _ (Thread/sleep 2000)
            {body :body} (pav-req :get "/user/me/timeline" liker-token {})
            {timeline-events :results} body]
        ;;check timeline event has correct author id
        (select-keys (first timeline-events) [:author :user_id]) => {:author author_user_id :user_id liker_user_id}
        (keys (first timeline-events)) => (just [:event_id :user_id :bill_id :comment_id :type :bill_title
                                                 :author :author_img_url :author_first_name :author_last_name
                                                 :disliked :liked :timestamp :body :score] :in-any-order)))

    (fact "Given a comment, When another user dislikes the comment, Then verify its in the users timeline."
      (let [;;Create Scorer
            {liker :body} (pav-req :put "/user" (new-pav-user))
            {disliker_user_id :user_id liker-token :token} liker
            ;;Create Author
            {author :body} (pav-req :put "/user" (new-pav-user))
            {author_user_id :user_id author_token :token} author
            {body :body} (pav-req :put "/bills/comments" author_token test-comment)
            {comment_id :comment_id} body
            _ (pav-req :post (str "/comments/" comment_id "/dislike") liker-token {:bill_id "hr2-114"})
            _ (Thread/sleep 3000)
            {body :body} (pav-req :get "/user/me/timeline" liker-token {})
            {timeline-events :results} body]
        ;;check timeline event has correct author id and user_id
        (select-keys (first timeline-events) [:author :user_id]) => {:author author_user_id :user_id disliker_user_id}
        (keys (first timeline-events)) => (just [:event_id :user_id :bill_id :comment_id :type :bill_title
                                                 :author :author_img_url :author_first_name :author_last_name
                                                 :disliked :liked :timestamp :body :score] :in-any-order)))

    (fact "Given a comment, When a user replies to the comment, Then verify the author receives a notification."
      (let [;;Create Scorer
            {replier :body} (pav-req :put "/user" (new-pav-user))
            {replier_user_id :user_id replier-token :token} replier
            ;;Create Author
            {author :body} (pav-req :put "/user" (new-pav-user))
            {author_token :token} author
            {body :body} (pav-req :put "/bills/comments" author_token test-comment)
            {comment_id :comment_id} body
            _ (pav-req :put (str "/comments/" comment_id "/reply") replier-token test-comment)
            _ (Thread/sleep 2000)
            {body :body} (pav-req :get "/user/notifications" author_token {})
            {notifications :results} body]
        ;;check notifications event has correct author id
        (get-in (first notifications) [:author]) => replier_user_id
        (keys (first notifications)) => (just [:notification_id :user_id :bill_id :comment_id :type :bill_title :read
                                               :author :author_img_url :author_first_name :author_last_name :parent_id
                                               :disliked :liked :timestamp :body :score] :in-any-order)))

    ))
