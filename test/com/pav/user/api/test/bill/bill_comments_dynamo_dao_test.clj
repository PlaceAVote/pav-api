(ns com.pav.user.api.test.bill.bill-comments-dynamo-dao-test
  (:use midje.sweet)
  (:require [com.pav.user.api.test.utils.utils :as u]
            [com.pav.user.api.dynamodb.comments :as dc]
            [com.pav.user.api.services.votes :as vs])
  (:import (java.util Date)))

(defn create-vote-records []
  (doseq [v [{:vote-id "1" :vote true :user_id "1234" :bill_id "hr2-114" :timestamp 12312321}
             {:vote-id "2" :vote false :user_id "5678" :bill_id "hr2-114" :timestamp 12312321}
             {:vote-id "3" :vote false :user_id "2468" :bill_id "hr2-114" :timestamp 12312321}]]
    (vs/create-user-vote-record v)))

(against-background [(before :facts (do (u/flush-dynamo-tables)
                                        (create-vote-records)))]

  (facts "Test cases cover the creation of bill comments in dynamodb"

    (fact "Persist new Bill Comment to DynamoDB & retrieve by id"
      (let [new-comment {:score 5 :bill_id "hr2-114"
                         :author     "user1" :timestamp (.getTime (Date.))
                         :comment_id "comment:1" :has_children false
                         :parent_id nil
                         :body       "comment body goes here!!!"}
            _ (dc/create-comment new-comment)
            persisted-comment (dc/get-bill-comments "hr2-114" :user_id "user1")]
        (:total persisted-comment) => 1
        (:comments persisted-comment) => (contains (assoc new-comment :replies [] :liked false :disliked false))))

    (fact "Persist new Bill Comment to DynamoDB & retrieve by id, When user_id is missing, Then verify comments are still present."
      (let [new-comment {:score 5 :bill_id "hr2-114"
                         :author     "user1" :timestamp (.getTime (Date.))
                         :comment_id "comment:1" :has_children false
                         :parent_id nil
                         :body       "comment body goes here!!!"}
            _ (dc/create-comment new-comment)
            persisted-comment (dc/get-bill-comments "hr2-114")]
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
            _ (dc/create-comment parent-comment)
            _ (dc/create-comment reply-to-parent)
            _ (dc/create-comment reply-to-comment)
            persisted-comment (dc/get-bill-comments "hr2-114" :user_id "user1")]
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
            _ (dc/create-comment lowest-scored)
            _ (dc/create-comment higher-scored)
            persisted-comment (dc/get-bill-comments "hr2-114" :user_id "user1" :sort-by :highest-score)]
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
            _ (dc/create-comment least-recent)
            _ (dc/create-comment most-recent)
            persisted-comment (dc/get-bill-comments "hr2-114" :user_id "user1" :sort-by :latest)]
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
            _ (dc/create-comment lowest-scored)
            _ (dc/create-comment higher-scored)
            persisted-comment (dc/get-bill-comments "hr2-114" :user_id "user1" :sort-by :highest-score)]
        (:total persisted-comment) => 2))

    (fact "Like a comment and ensure correct ordering by score"
      (let [new-comment {:score 0 :bill_id "hr2-114"
                         :author "user1" :timestamp (.getTime (Date.))
                         :comment_id "comment:0" :has_children false
                         :parent_id nil
                         :body "comment body goes here!!!"}
            _ (dc/create-comment new-comment)
            _ (dc/score-comment "comment:0" "user1" :like)
            ;;create number of comment replies to ensure there is more than one comment in the result payload.
            _ (dotimes [n 11]
                (dc/create-comment
                  {:score      0 :bill_id "hr2-114"
                   :author     "user1" :timestamp (.getTime (Date.))
                   :comment_id (str "comment:" (inc n)) :has_children false
                   :parent_id  "comment:0"
                   :body       "comment body goes here!!!"}))
            persisted-comment (dc/get-bill-comments "hr2-114" :user_id "user1")]
        (first (:comments persisted-comment)) => (contains {:score 1})))

    (fact "Dislike a comment"
      (let [new-comment {:score 0 :bill_id "hr2-114"
                         :author "user1" :timestamp (.getTime (Date.))
                         :comment_id "comment:0" :has_children false
                         :parent_id nil
                         :body "comment body goes here!!!"}
            _ (dc/create-comment new-comment)
            _ (dc/score-comment "comment:0" "user1" :dislike)
            ;;create number of comment replies to ensure there is more than one comment in the result payload.
            _ (dotimes [n 11]
                (dc/create-comment
                  {:score      0 :bill_id "hr2-114"
                   :author     "user1" :timestamp (.getTime (Date.))
                   :comment_id (str "comment:" (inc n)) :has_children false
                   :parent_id  "comment:0"
                   :body       "comment body goes here!!!"}))
            persisted-comment (dc/get-bill-comments "hr2-114" :user_id "user1")]
        (:total persisted-comment) => 1
        (first (:comments persisted-comment)) => (contains {:score -1})))

    (fact "Retrieve top comments for a bill"
      (let [comment-for {:score 2 :bill_id "hr2-114"
                         :author "1234" :timestamp (.getTime (Date.))
                         :comment_id "comment:1" :has_children false
                         :parent_id nil
                         :body "I love this bill!!!"}
            comment-against {:score 4 :bill_id "hr2-114"
                             :author "5678" :timestamp (.getTime (Date.))
                             :comment_id "comment:2" :has_children false
                             :parent_id nil
                             :body "I hate this bill !!!"}
            comment-against2 {:score 6 :bill_id "hr2-114"
                              :author "2468" :timestamp (.getTime (Date.))
                              :comment_id "comment:3" :has_children false
                              :parent_id nil
                              :body "I hate this bill !!!"}
            comment-with-no-vote {:score 4 :bill_id "hr2-114"
                                  :author "6789" :timestamp (.getTime (Date.))
                                  :comment_id "comment:4" :has_children false
                                  :parent_id nil
                                  :body "I haven't voted dummy !!!"}
            comment2-with-no-vote {:score 6 :bill_id "hr2-114"
                                   :author "6789" :timestamp (.getTime (Date.))
                                   :comment_id "comment:5" :has_children false
                                   :parent_id nil
                                   :body "I haven't voted dummy !!!"}
            _ (dc/create-comment comment-for)
            _ (dc/create-comment comment-against)
            _ (dc/create-comment comment-against2)
            _ (dc/create-comment comment-with-no-vote)
            _ (dc/create-comment comment2-with-no-vote)
            top-comments (dc/get-top-comments "hr2-114" "5678")]
        top-comments => {:for-comment     (assoc comment-for :liked false :disliked false)
                         :against-comment (assoc comment-against2 :liked false :disliked false)}))

    (fact "Retrieve top comments for a bill, When user_id is nil, Then there should be no liked or disliked status"
      (let [comment-for {:score 2 :bill_id "hr2-114"
                         :author "1234" :timestamp (.getTime (Date.))
                         :comment_id "comment:1" :has_children false
                         :parent_id nil
                         :body "I love this bill!!!"}
            comment-against {:score 4 :bill_id "hr2-114"
                             :author "5678" :timestamp (.getTime (Date.))
                             :comment_id "comment:2" :has_children false
                             :parent_id nil
                             :body "I hate this bill !!!"}
            comment-against2 {:score 6 :bill_id "hr2-114"
                              :author "2468" :timestamp (.getTime (Date.))
                              :comment_id "comment:3" :has_children false
                              :parent_id nil
                              :body "I hate this bill !!!"}
            comment-with-no-vote {:score 4 :bill_id "hr2-114"
                                  :author "6789" :timestamp (.getTime (Date.))
                                  :comment_id "comment:4" :has_children false
                                  :parent_id nil
                                  :body "I haven't voted dummy !!!"}
            comment2-with-no-vote {:score 6 :bill_id "hr2-114"
                                   :author "6789" :timestamp (.getTime (Date.))
                                   :comment_id "comment:5" :has_children false
                                   :parent_id nil
                                   :body "I haven't voted dummy !!!"}
            _ (dc/create-comment comment-for)
            _ (dc/create-comment comment-against)
            _ (dc/create-comment comment-against2)
            _ (dc/create-comment comment-with-no-vote)
            _ (dc/create-comment comment2-with-no-vote)
            top-comments (dc/get-top-comments "hr2-114")]
        top-comments => {:for-comment     (assoc comment-for :liked false :disliked false)
                         :against-comment (assoc comment-against2 :liked false :disliked false)}))

    (fact "Don't return top comments if there is one comment for with a corresponding vote but none against"
      (let [comment-for {:score 2 :bill_id "hr2-114"
                         :author "1234" :timestamp (.getTime (Date.))
                         :comment_id "comment:1" :has_children false
                         :parent_id nil
                         :body "I love this bill!!!"}
            _ (dc/create-comment comment-for)
            top-comments (dc/get-top-comments "hr2-114" "5678")]
        top-comments => {:for-comment []
                         :against-comment []}))

    (fact "Don't return top comments if there is one comment against with a corresponding vote but none for"
      (let [comment-against {:score 4 :bill_id "hr2-114"
                             :author "5678" :timestamp (.getTime (Date.))
                             :comment_id "comment:2" :has_children false
                             :parent_id nil
                             :body "I hate this bill !!!"}
            _ (dc/create-comment comment-against)
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
            _ (dc/create-comment lowest-scored)
            _ (dc/create-comment higher-scored)
            {last_comment_id :last_comment_id} (dc/get-bill-comments "hr2-114" :user_id "user1" :highest-score :latest :limit 1)
            persisted-comment (dc/get-bill-comments "hr2-114" :user_id "user1" :sort-by :highest-score :last_comment_id last_comment_id)]
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
            _ (dc/create-comment least-recent)
            _ (dc/create-comment most_recent)
            {last_comment_id :last_comment_id} (dc/get-bill-comments "hr2-114" :user_id "user1" :sort-by :latest :limit 1)
            persisted-comment (dc/get-bill-comments "hr2-114" :user_id "user1" :sort-by :latest :last_comment_id last_comment_id)]
        (:total persisted-comment) => 1
        (:comments persisted-comment) => [(assoc least-recent :replies [] :liked false :disliked false)]))))


