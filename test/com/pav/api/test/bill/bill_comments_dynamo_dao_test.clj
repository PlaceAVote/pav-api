(ns com.pav.api.test.bill.bill-comments-dynamo-dao-test
  (:use midje.sweet)
  (:require [com.pav.api.test.utils.utils :as u]
            [com.pav.api.dynamodb.comments :as dc]
            [com.pav.api.services.votes :as vs])
  (:import (java.util Date)))

(against-background [(before :facts (u/flush-dynamo-tables))]

  (facts "Test cases cover the creation of bill comments in dynamodb"

    (fact "Persist new Bill Comment to DynamoDB & retrieve by id"
      (let [new-comment {:score 5 :bill_id "hr2-114"
                         :author     "user1" :timestamp (.getTime (Date.))
                         :comment_id "comment:1" :has_children false
                         :parent_id nil
                         :body       "comment body goes here!!!"}
            _ (dc/create-bill-comment new-comment)
            persisted-comment (dc/get-user-bill-comments "hr2-114" :user_id "user1")]
        (:total persisted-comment) => 1
        (:comments persisted-comment) => (contains (assoc new-comment :replies [] :liked false :disliked false))))

    (fact "Update an existing comment"
      (let [new-comment {:score 5 :bill_id "hr2-114"
                         :author     "user1" :timestamp (.getTime (Date.))
                         :comment_id "comment:1" :has_children false
                         :parent_id nil
                         :body       "comment body goes here!!!"}
            _ (dc/create-bill-comment new-comment)
            _ (dc/update-bill-comment {:body "updated comment body"} "comment:1")
            persisted-comment (dc/get-user-bill-comments "hr2-114")]
        (:total persisted-comment) => 1
        (get-in (first (:comments persisted-comment)) [:body]) => "updated comment body"))

    (fact "Mark comment as deleted, Then verify the deleted flag, updated_at are correct and the body is empty on retrieval."
      (let [new-comment {:score 5 :bill_id "hr2-114" :author "user1" :timestamp (.getTime (Date.))
                         :comment_id "comment:1" :has_children false :parent_id nil
                         :body "comment body goes here!!!"}
            _ (dc/create-bill-comment new-comment)]
        (dc/delete-comment "comment:1" "user1") => (contains {:deleted true :updated_at anything} :in-any-order)
        (get-in (:comments (dc/get-user-bill-comments "hr2-114")) [:body]) => nil))

    (fact "Persist new Bill Comment to DynamoDB & retrieve by id, When user_id is missing, Then verify comments are still present."
      (let [new-comment {:score 5 :bill_id "hr2-114"
                         :author     "user1" :timestamp (.getTime (Date.))
                         :comment_id "comment:1" :has_children false
                         :parent_id nil
                         :body       "comment body goes here!!!"}
            _ (dc/create-bill-comment new-comment)
            persisted-comment (dc/get-user-bill-comments "hr2-114")]
        (:total persisted-comment) => 1
        (:comments persisted-comment) => (contains (assoc new-comment :replies [] :liked false :disliked false))))

    (fact "Reply to existing comment and retrieve parent and nested reply"
      (let [parent-comment {:score 5 :bill_id "hr2-114"
                            :author "user1" :timestamp (.getTime (Date.))
                            :comment_id "comment:1" :has_children false
                            :parent_id nil
                            :body "comment body goes here!!!"}
            reply-to-parent {:score 0 :bill_id "hr2-114"
                             :author "user12" :timestamp (.getTime (Date.))
                             :comment_id "comment:2" :has_children false
                             :parent_id "comment:1"
                             :body "Reply body goes here!!!"}
            reply-to-comment {:score 0 :bill_id "hr2-114"
                              :author "user123" :timestamp (.getTime (Date.))
                              :comment_id "comment:3" :has_children false
                              :parent_id "comment:2"
                              :body "Comment 2 Reply body goes here!!!"}
            _ (dc/create-bill-comment parent-comment)
            _ (dc/create-bill-comment reply-to-parent)
            _ (dc/create-bill-comment reply-to-comment)
            persisted-comment (dc/get-user-bill-comments "hr2-114" :user_id "user1")]
        (:total persisted-comment) => 1
        (first (:comments persisted-comment)) => (contains {:has_children true})
        (first (get-in (first (:comments persisted-comment)) [:replies])) =>
        (contains {:score 0 :bill_id "hr2-114"
                   :author "user12"
                   :comment_id "comment:2" :has_children true
                   :parent_id "comment:1"
                   :body "Reply body goes here!!!"
                   :liked false
                   :disliked false})))

    (fact "Persist two new Comments, when querying by id, ensure sorting by score"
      (let [higher-scored {:score 5 :bill_id "hr2-114"
                           :author     "user1" :timestamp (.getTime (Date.))
                           :comment_id "comment:1" :has_children false
                           :parent_id nil
                           :body       "comment body goes here!!!"}
            lowest-scored {:score 2 :bill_id "hr2-114"
                           :author "user2" :timestamp (.getTime (Date.))
                           :comment_id "comment:2" :has_children false
                           :parent_id nil
                           :body "comment 2 body goes here!!!"}
            _ (dc/create-bill-comment lowest-scored)
            _ (dc/create-bill-comment higher-scored)
            persisted-comment (dc/get-user-bill-comments "hr2-114" :user_id "user1" :sort-by :highest-score)]
        (:total persisted-comment) => 2
        (:comments persisted-comment) => [(assoc higher-scored :replies [] :liked false :disliked false)
                                          (assoc lowest-scored :replies [] :liked false :disliked false)]))

    (fact "Persist two new Comments, When sorting by most recent comments, ensure sorting by timestamp"
      (let [least-recent {:score 5 :bill_id "hr2-114"
                          :author     "user1" :timestamp 1457347973000
                          :comment_id "comment:1" :has_children false
                          :parent_id nil
                          :body       "comment body goes here!!!"}
            most-recent {:score 2 :bill_id "hr2-114"
                         :author "user2" :timestamp 1457434373000
                         :comment_id "comment:2" :has_children false
                         :parent_id nil
                         :body "comment 2 body goes here!!!"}
            _ (dc/create-bill-comment least-recent)
            _ (dc/create-bill-comment most-recent)
            persisted-comment (dc/get-user-bill-comments "hr2-114" :user_id "user1" :sort-by :latest)]
        (:total persisted-comment) => 2
        (:comments persisted-comment) => [(assoc most-recent :replies [] :liked false :disliked false)
                                          (assoc least-recent :replies [] :liked false :disliked false)]))

    (fact "Persist two new Comments, when both comments have the same score, ensure both results are returned"
      (let [higher-scored {:score 2 :bill_id "hr2-114"
                           :author     "user1" :timestamp (.getTime (Date.))
                           :comment_id "comment:1" :has_children false
                           :parent_id nil
                           :body       "comment body goes here!!!"}
            lowest-scored {:score 2 :bill_id "hr2-114"
                           :author "user2" :timestamp (.getTime (Date.))
                           :comment_id "comment:2" :has_children false
                           :parent_id nil
                           :body "comment 2 body goes here!!!"}
            _ (dc/create-bill-comment lowest-scored)
            _ (dc/create-bill-comment higher-scored)
            persisted-comment (dc/get-user-bill-comments "hr2-114" :user_id "user1" :sort-by :highest-score)]
        (:total persisted-comment) => 2))

    (fact "Like a comment and ensure correct ordering by score"
      (let [new-comment {:score 0 :bill_id "hr2-114"
                         :author "user1" :timestamp (.getTime (Date.))
                         :comment_id "comment:0" :has_children false
                         :parent_id nil
                         :body "comment body goes here!!!"}
            _ (dc/create-bill-comment new-comment)
            _ (dc/score-comment {:comment_id "comment:0" :user_id "user1" :liked :like})
            ;;create number of comment replies to ensure there is more than one comment in the result payload.
            _ (dotimes [n 11]
                (dc/create-bill-comment
                  {:score      0 :bill_id "hr2-114"
                   :author     "user1" :timestamp (.getTime (Date.))
                   :comment_id (str "comment:" (inc n)) :has_children false
                   :parent_id  "comment:0"
                   :body       "comment body goes here!!!"}))
            persisted-comment (dc/get-user-bill-comments "hr2-114" :user_id "user1")]
        (first (:comments persisted-comment)) => (contains {:score 1})))

    (fact "Dislike a comment"
      (let [new-comment {:score 0 :bill_id "hr2-114"
                         :author "user1" :timestamp (.getTime (Date.))
                         :comment_id "comment:0" :has_children false
                         :parent_id nil
                         :body "comment body goes here!!!"}
            _ (dc/create-bill-comment new-comment)
            _ (dc/score-comment {:comment_id "comment:0" :user_id "user1" :liked :dislike})
            ;;create number of comment replies to ensure there is more than one comment in the result payload.
            _ (dotimes [n 11]
                (dc/create-bill-comment
                  {:score      0 :bill_id "hr2-114"
                   :author     "user1" :timestamp (.getTime (Date.))
                   :comment_id (str "comment:" (inc n)) :has_children false
                   :parent_id  "comment:0"
                   :body       "comment body goes here!!!"}))
            persisted-comment (dc/get-user-bill-comments "hr2-114" :user_id "user1")]
        (:total persisted-comment) => 1
        (first (:comments persisted-comment)) => (contains {:score -1})))

    (fact "Retrieve top comments for a bill"
      (let [user1 (u/create-user)
            _ (vs/create-user-vote-record {:vote true :bill_id "hr2-114"} (:user_id user1))
            user2 (u/create-user)
            _ (vs/create-user-vote-record {:vote false :bill_id "hr2-114"} (:user_id user2))
            user3 (u/create-user)
            _ (vs/create-user-vote-record {:vote false :bill_id "hr2-114"} (:user_id user3))
            user4 (u/create-user)
            comment-for {:score 2 :bill_id "hr2-114"
                         :author (:user_id user1) :timestamp (.getTime (Date.))
                         :comment_id "comment:1" :has_children false
                         :parent_id nil
                         :body "I love this bill!!!"}
            comment-against {:score 4 :bill_id "hr2-114"
                             :author (:user_id user2) :timestamp (.getTime (Date.))
                             :comment_id "comment:2" :has_children false
                             :parent_id nil
                             :body "I hate this bill !!!"}
            comment-against2 {:score 6 :bill_id "hr2-114"
                              :author (:user_id user3) :timestamp (.getTime (Date.))
                              :comment_id "comment:3" :has_children false
                              :parent_id nil
                              :body "I hate this bill !!!"}
            comment-with-no-vote {:score 4 :bill_id "hr2-114"
                                  :author (:user_id user4) :timestamp (.getTime (Date.))
                                  :comment_id "comment:4" :has_children false
                                  :parent_id nil
                                  :body "I haven't voted dummy !!!"}
            comment2-with-no-vote {:score 6 :bill_id "hr2-114"
                                   :author (:user_id user4) :timestamp (.getTime (Date.))
                                   :comment_id "comment:5" :has_children false
                                   :parent_id nil
                                   :body "I haven't voted dummy !!!"}
            _ (dc/create-bill-comment comment-for)
            _ (dc/create-bill-comment comment-against)
            _ (dc/create-bill-comment comment-against2)
            _ (dc/create-bill-comment comment-with-no-vote)
            _ (dc/create-bill-comment comment2-with-no-vote)
            top-comments (dc/get-top-comments "hr2-114" (:user_id user2))]
        (:for-comment top-comments) => (contains (assoc comment-for :liked false :disliked false) :in-any-order)
        (:against-comment top-comments) => (contains (assoc comment-against2 :liked false :disliked false) :in-any-order)))

    (fact "Retrieve top comments for a bill, When user_id is nil, Then there should be no liked or disliked status" 
      (let [user1 (u/create-user)
            _ (vs/create-user-vote-record {:vote true :bill_id "hr2-114"} (:user_id user1))
            user2 (u/create-user)
            _ (vs/create-user-vote-record {:vote false :bill_id "hr2-114"} (:user_id user2))
            user3 (u/create-user)
            _ (vs/create-user-vote-record {:vote false :bill_id "hr2-114"} (:user_id user3))
            user4 (u/create-user)
            comment-for {:score 2 :bill_id "hr2-114"
                         :author (:user_id user1) :timestamp (.getTime (Date.))
                         :comment_id "comment:1" :has_children false
                         :parent_id nil
                         :body "I love this bill!!!"}
            comment-against {:score 4 :bill_id "hr2-114"
                             :author (:user_id user2) :timestamp (.getTime (Date.))
                             :comment_id "comment:2" :has_children false
                             :parent_id nil
                             :body "I hate this bill !!!"}
            comment-against2 {:score 6 :bill_id "hr2-114"
                              :author (:user_id user3) :timestamp (.getTime (Date.))
                              :comment_id "comment:3" :has_children false
                              :parent_id nil
                              :body "I hate this bill !!!"}
            comment-with-no-vote {:score 4 :bill_id "hr2-114"
                                  :author (:user_id user4) :timestamp (.getTime (Date.))
                                  :comment_id "comment:4" :has_children false
                                  :parent_id nil
                                  :body "I haven't voted dummy !!!"}
            comment2-with-no-vote {:score 6 :bill_id "hr2-114"
                                   :author (:user_id user4) :timestamp (.getTime (Date.))
                                   :comment_id "comment:5" :has_children false
                                   :parent_id nil
                                   :body "I haven't voted dummy !!!"}
            _ (dc/create-bill-comment comment-for)
            _ (dc/create-bill-comment comment-against)
            _ (dc/create-bill-comment comment-against2)
            _ (dc/create-bill-comment comment-with-no-vote)
            _ (dc/create-bill-comment comment2-with-no-vote)
            top-comments (dc/get-top-comments "hr2-114")]
        (:for-comment top-comments) => (contains (assoc comment-for :liked false :disliked false) :in-any-order)
        (:against-comment top-comments) => (contains (assoc comment-against2 :liked false :disliked false) :in-any-order)))

    (fact "Don't return top comments if there is one comment for with a corresponding vote but none against"
      (let [comment-for {:score 2 :bill_id "hr2-114"
                         :author "1234" :timestamp (.getTime (Date.))
                         :comment_id "comment:1" :has_children false
                         :parent_id nil
                         :body "I love this bill!!!"}
            _ (dc/create-bill-comment comment-for)
            top-comments (dc/get-top-comments "hr2-114" "5678")]
        top-comments => {:for-comment []
                         :against-comment []}))

    (fact "Don't return top comments if there is one comment against with a corresponding vote but none for"
      (let [comment-against {:score 4 :bill_id "hr2-114"
                             :author "5678" :timestamp (.getTime (Date.))
                             :comment_id "comment:2" :has_children false
                             :parent_id nil
                             :body "I hate this bill !!!"}
            _ (dc/create-bill-comment comment-against)
            top-comments (dc/get-top-comments "hr2-114" "5678")]
        top-comments => {:for-comment []
                         :against-comment []}))

    (fact "Persist two new Comments, when querying with last_comment_id of the first comment and sorting by highest-score,
         ensure only one record is returned"
      (let [higher-scored {:score 5 :bill_id "hr2-114"
                           :author     "user1" :timestamp (.getTime (Date.))
                           :comment_id "comment:1" :has_children false
                           :parent_id nil
                           :body       "comment body goes here!!!"}
            lowest-scored {:score 2 :bill_id "hr2-114"
                           :author "user2" :timestamp (.getTime (Date.))
                           :comment_id "comment:2" :has_children false
                           :parent_id nil
                           :body "comment 2 body goes here!!!"}
            _ (dc/create-bill-comment lowest-scored)
            _ (dc/create-bill-comment higher-scored)
            {last_comment_id :last_comment_id} (dc/get-user-bill-comments "hr2-114" :user_id "user1" :highest-score :latest :limit 1)
            persisted-comment (dc/get-user-bill-comments "hr2-114" :user_id "user1" :sort-by :highest-score :last_comment_id last_comment_id)]
        (:total persisted-comment) => 1
        (:comments persisted-comment) => [(assoc lowest-scored :replies [] :liked false :disliked false)]))

    (fact "Persist two new Comments, when querying with last_comment_id of the first comment and sorting by timestamp,
        ensure only one record is returned"
      (let [most_recent {:score 5 :bill_id "hr2-114"
                         :author     "user1" :timestamp 1458147015000
                         :comment_id "comment:1" :has_children false
                         :parent_id nil
                         :body       "comment body goes here!!!"}
            least-recent {:score 2 :bill_id "hr2-114"
                          :author "user2" :timestamp 1458060615000
                          :comment_id "comment:2" :has_children false
                          :parent_id nil
                          :body "comment 2 body goes here!!!"}
            _ (dc/create-bill-comment least-recent)
            _ (dc/create-bill-comment most_recent)
            {last_comment_id :last_comment_id} (dc/get-user-bill-comments "hr2-114" :user_id "user1" :sort-by :latest :limit 1)
            persisted-comment (dc/get-user-bill-comments "hr2-114" :user_id "user1" :sort-by :latest :last_comment_id last_comment_id)]
        (:total persisted-comment) => 1
        (:comments persisted-comment) => [(assoc least-recent :replies [] :liked false :disliked false)]))))


