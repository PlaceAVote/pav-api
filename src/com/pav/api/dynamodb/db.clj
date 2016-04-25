(ns com.pav.api.dynamodb.db
  (:require [taoensso.faraday :as far]
            [environ.core :refer [env]]
            [clojure.tools.logging :as log]))

;;; client options

(def ^{:doc "General access details for DynamoDB. Read from project.clj."}
  client-opts {:access-key (:access-key env)
               :secret-key (:secret-key env)
               :endpoint (:dynamo-endpoint env)})

;;; tables

(def ^{:doc "Users table."}
  user-table-name (:dynamo-user-table-name env))

(def ^{:doc "User confirm table"}
  user-confirm-table-name (:dynamo-user-confirmation-table-name env))

(def ^{:doc "Notifications table."}
  notification-table-name (:dynamo-notification-table-name env))

(def ^{:doc "User feeds."}
  userfeed-table-name (:dynamo-userfeed-table-name env))

(def ^{:doc "User timeline table."}
  timeline-table-name (:dynamo-usertimeline-table-name env))

(def ^{:doc "Followers."}
  follower-table-name (:dynamo-follower-table-name env))

(def ^{:doc "Types user is following."}
  following-table-name (:dynamo-following-table-name env))

(def ^{:doc "Table used to keep track of Parent Comments and There Threads"}
  bill-comment-table-name (:dynamo-bill-comments-table-name env))

(def ^{:doc "Comment details."}
  comment-details-table-name (:dynamo-comment-details-table-name env))

(def ^{:doc "Record users scoring per comment."}
  comment-user-scoring-table (:dynamo-comment-user-scoring-table env))

(def ^{:doc "User Votes table"}
  user-votes-table-name (:dynamo-user-votes-table env))

(def ^{:doc "Vote counters."}
  vote-count-table-name (:dynamo-vote-count-table env))

(def ^{:doc "Questions."}
  question-table-name (:dynamo-questions-table env))

(def ^{:doc "Table for user answers to above questions."}
  user-question-answers-table-name (:dynamo-user-question-answers-table env))

(def ^{:doc "Table for user bill issues."}
  user-issues-table-name (:dynamo-user-issues-table env))

(def ^{:doc "Bill issue responses."}
  user-issue-responses-table-name (:dynamo-user-issue-responses-table env))

(def ^{:doc "User ISSUE comments table."}
  user-issue-comments-table-name (:dynamo-user-issue-comments-table-name env))

(def ^{:doc "Record users scoring per comment."}
  user-issue-comments-scoring-table (:dynamo-user-issue-comments-scoring-table env))

;;; code

(defn- safe-create-table
  "Same as 'far/ensure-table', except it will log when new tables are created.
This way we can easily track in logs when new tables are added. 

It will NOT handle exceptions."
  [client-opts table-name hash-keydef & [opts]]
  (when-not (far/describe-table client-opts table-name)
    (log/infof "Creating table '%s'..." table-name)
    (far/create-table client-opts table-name hash-keydef opts)))

(defn create-all-tables!
  "Create all dynamodb tables. Skips if tables are already present."
  ([opts]
     (log/debug "Creating all dynamo tables...")
     (try
       (safe-create-table opts user-table-name [:user_id :s]
         {:gsindexes [{:name "user-email-idx"
                       :hash-keydef [:email :s]
                       :throughput {:read 5 :write 10}}
                      {:name "fbid-idx"
                       :hash-keydef [:facebook_id :s]
                       :throughput {:read 5 :write 10}}]
          :throughput {:read 5 :write 10}
          :block? true})
       (safe-create-table opts user-confirm-table-name [:confirmation-token :s]
         {:throughput {:read 5 :write 10}
          :block? true})
       (safe-create-table opts notification-table-name [:user_id :s]
         {:gsindexes [{:name "notification_id-idx"
                       :hash-keydef [:notification_id :s]
                       :throughput {:read 5 :write 10}}]
          :range-keydef [:timestamp :n]
          :throughput {:read 5 :write 10}
          :block? true})
       (safe-create-table opts timeline-table-name [:user_id :s]
         {:range-keydef [:timestamp :n]
          :throughput {:read 5 :write 10}
          :block? true})
       (safe-create-table opts userfeed-table-name [:user_id :s]
         {:gsindexes [{:name "issueid-idx"
                       :hash-keydef [:issue_id :s]
                       :throughput {:read 5 :write 5}}
                      {:name "commentid-idx"
                       :hash-keydef [:comment_id :s]
                       :throughput {:read 5 :write 5}}]
          :range-keydef [:timestamp :n]
          :throughput {:read 5 :write 10}
          :block? true})
       (safe-create-table opts following-table-name [:user_id :s]
         {:range-keydef [:following :s]
          :throughput {:read 5 :write 10}
          :block? true})
       (safe-create-table opts follower-table-name [:user_id :s]
         {:range-keydef [:follower :s]
          :throughput {:read 5 :write 10}
          :block? true})
       (safe-create-table opts user-votes-table-name [:vote-id :s]
         {:gsindexes [{:name "user-bill-idx"
                       :hash-keydef [:user_id :s]
                       :range-keydef [:bill_id :s]
                       :throughput {:read 5 :write 5}}
                      {:name "bill-user-idx"
                       :hash-keydef [:bill_id :s]
                       :range-keydef [:user_id :s]
                       :throughput {:read 5 :write 5}}]
          :throughput {:read 5 :write 5}
          :block? true})
       (safe-create-table opts vote-count-table-name [:bill_id :s]
         {:throughput {:read 5 :write 10}
          :block? true})
       (safe-create-table opts bill-comment-table-name [:id :s]
         {:range-keydef [:comment_id :s]
          :throughput {:read 5 :write 10}
          :gsindexes [{:name "comment-idx"
                       :hash-keydef [:comment_id :s]
                       :throughput {:read 5 :write 5}}
                      {:name "bill-score-idx"
                       :hash-keydef [:id :s]
                       :range-keydef [:score :n]
                       :throughput {:read 5 :write 10}}
                      {:name "bill-timestamp-idx"
                       :hash-keydef [:id :s]
                       :range-keydef [:timestamp :n]
                       :throughput {:read 5 :write 10}}]
          :block? true})
       (safe-create-table opts comment-details-table-name [:comment_id :s]
         {:gsindexes [{:name "bill-comment-idx"
                       :hash-keydef [:bill_id :s]
                       :range-keydef [:comment_id :s]
                       :throughput {:read 5 :write 10}}]
          :throughput {:read 5 :write 10}
          :block? true})
       (safe-create-table opts comment-user-scoring-table [:comment_id :s]
         {:range-keydef [:user_id :s]
          :throughput {:read 5 :write 10}
          :block? true})
       (safe-create-table opts question-table-name [:topic :s]
         {:range-keydef [:question_id :s]
          :throughput {:read 5 :write 10}
          :block? true})
       (safe-create-table opts user-question-answers-table-name [:user_id :s]
         {:range-keydef [:question_id :s]
          :throughput {:read 5 :write 10}
          :block? true})
       (safe-create-table opts user-issues-table-name [:issue_id :s]
         {:range-keydef [:user_id :s]
          :gsindexes [{:name "user-issues-idx"
                       :hash-keydef [:user_id :s]
                       :range-keydef [:issue_id :s]
                       :throughput {:read 5 :write 10}}
                      {:name "bill-issues-idx"
                       :hash-keydef [:bill_id :s]
                       :range-keydef [:issue_id :s]
                       :throughput {:read 5 :write 10}}]
          :throughput {:read 5 :write 10}
          :block? true})
       (safe-create-table opts user-issue-responses-table-name [:issue_id :s]
         {:range-keydef [:user_id :s]
          :throughput {:read 5 :write 10}
          :block? true})
       (safe-create-table opts user-issue-comments-table-name [:comment_id :s]
         {:gsindexes [{:name "issueid-timestamp-idx"
                       :hash-keydef [:issue_id :s]
                       :range-keydef [:timestamp :n]
                       :throughput {:read 5 :write 10}}
                      {:name "issueid-score-idx"
                       :hash-keydef [:issue_id :s]
                       :range-keydef [:score :n]
                       :throughput {:read 5 :write 10}}]
          :throughput {:read 5 :write 10}
          :block? true})
       (safe-create-table opts user-issue-comments-scoring-table [:comment_id :s]
         {:range-keydef [:user_id :s]
          :throughput {:read 5 :write 10}
          :block? true})
       (log/debug "Creating tables done")
       (catch Exception e 
         (log/error e (str "Failed with creating one of the tables with: " opts)))))
  ([] (create-all-tables! client-opts)))

(defn- safe-delete-table
  "Capture in case 'table' got nil (probably due bad configuration).
It will handle exceptions, so it can be safely called inside 'delete-all-tables!'."
  [opts table]
  (when table
    (try
      (far/delete-table opts table)
      (catch com.amazonaws.services.dynamodbv2.model.ResourceNotFoundException _
        ;; do nothing, as we aren't going to log failing deletion when table
        ;; is not present
        )
      (catch Exception e
        (log/error e (str "Unable to delete table: " table))
        false))))

(defn delete-all-tables!
  "Clear all dynamodb tables."
  ([opts]
     (log/debug "Deleting all dynamo tables...")
     (safe-delete-table opts user-table-name)
     (safe-delete-table opts user-confirm-table-name)
     (safe-delete-table opts notification-table-name)
     (safe-delete-table opts timeline-table-name)
     (safe-delete-table opts follower-table-name)
     (safe-delete-table opts following-table-name)
     (safe-delete-table opts userfeed-table-name)
     (safe-delete-table opts vote-count-table-name)
     (safe-delete-table opts user-votes-table-name)
     (safe-delete-table opts bill-comment-table-name)
     (safe-delete-table opts comment-details-table-name)
     (safe-delete-table opts comment-user-scoring-table)
     (safe-delete-table opts question-table-name)
     (safe-delete-table opts user-question-answers-table-name)
     (safe-delete-table opts user-issues-table-name)
     (safe-delete-table opts user-issue-comments-table-name)
     (safe-delete-table opts user-issue-comments-scoring-table)
     (safe-delete-table opts user-issue-responses-table-name))
  ([] (delete-all-tables! client-opts)))

(defn recreate-all-tables!
  "Clears and create all tables."
  ([opts]
     (delete-all-tables! opts)
     (create-all-tables! opts))
  ([] (recreate-all-tables! client-opts)))
