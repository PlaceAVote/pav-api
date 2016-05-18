(ns com.pav.api.dbwrapper.from-dynamodb
  "Utility functions to move data from DynamoDB to SQL database."
  (:require [com.pav.api.dynamodb.user :as du]
            [com.pav.api.db.user :as su]
            [com.pav.api.dbwrapper.user :refer [convert-user-profile]]
            [com.pav.api.dynamodb.comments :as dc]
            [com.pav.api.db.comment :as sc]
            [com.pav.api.dbwrapper.comment :refer [dynamodb->sql-comment]]
            [com.pav.api.dynamodb.votes :as dv]
            [com.pav.api.db.vote :as sv]
            [com.pav.api.dbwrapper.vote :refer [dynamo-vote->sql-vote]]
            [clojure.tools.logging :as log]
            [com.pav.api.db.db :as db]))

(defn- migrate-user
  "Copy single user from dynamodb to sql table."
  [user]
  (let [email (:email user)]
    (if-let [found (su/get-user-by-email email)]
      (log/infof "Skipping user '%s' (found under id: %s)" email (:user_id found))
      (try
        (log/infof "Migrating user '%s'" email)
        (-> user convert-user-profile su/create-user)
        (catch Throwable e
          (log/errorf "Failed for '%s' with %s" email (.getMessage e)))))))

(defn- migrate-bill-comment
  "Copy single bill comment from dynamodb to sql table"
  [comment]
  (if-let [found (sc/get-bill-comment-by-old-id (:comment_id comment))]
    (log/infof "Skipping comment found using old id '%s'" (:comment_id found))
    (try
      (log/infof "Migrating comment '%s'" (:comment_id comment))
      (-> comment dynamodb->sql-comment sc/create-bill-comment)
      (catch Throwable e
        (log/errorf "Failed for comment '%s' with %s" (:comment_id comment) (.getMessage e))))))

(defn- migrate-bill-comment-score
  "Copy single bill comment scoring record from dynamodb to sql table"
  [{old_comment_id :comment_id old_user_id :user_id :as score}]
  (if-let [found (sc/get-bill-comment-score-by-old-ids old_comment_id old_user_id)]
    (log/infof "Skipping comment scoring record found using old_user_id '%s' and old_comment_id '%s'"
      (:user_id found) (:comment_id found))
    (try
      (log/infof "Migrating bill comment score for user '%s' and comment '%s'" old_user_id old_comment_id)
      (let [{existing-user :user_id} (su/get-user-by-old-id old_user_id)
            {existing-comment :comment_id} (sc/get-bill-comment-by-old-id old_comment_id)]
        (if (and existing-user existing-comment)
          (sc/insert-user-comment-scoring-record db/db existing-comment existing-user (if (:liked score) :like :dislike)
            :old_comment_id old_comment_id :old_user_id old_user_id)
          (log/infof "Could not find existing comment '%' and user '%s' in user_comment_scores table "
            old_comment_id old_user_id)))
      (catch Throwable e
        (log/errorf "Failed for bill comment scoring record for user '%s' and comment '%s' with %s"
          old_user_id old_comment_id (.getMessage e))))))

(defn- migrate-users
  "Copy all dynamodb users to sql user table."
  []
  (let [users (du/retrieve-all-user-records)]
    (doseq [u users]
      (migrate-user u))))

(defn- migrate-user-vote [{old_vote_id :vote-id :as v}]
  (if (sv/get-user-vote-by-old-id old_vote_id)
    (log/infof "Skipping user vote record found using old_vote_id '%s'" old_vote_id)
    (if (su/get-user-by-old-id (:user_id v))
      (try
        (do
         (log/infof "Migrating vote '%s'" old_vote_id)
         (-> v dynamo-vote->sql-vote sv/create-user-vote-record))
        (catch Throwable e
          (log/errorf "Failed migrating user vote for vote-id '%s' with %s" old_vote_id (.getMessage e))))
      (log/warnf "Could not find existing user '%s'" (:user_id v)))))

(defn- migrate-bill-comments
  "Copy all dynamodb bill comments to sql user_bill_comments and comments table"
  []
  (let [comments (dc/retrieve-all-bill-comments)
        scores (dc/retrieve-all-bill-comment-scores)]
    (doseq [c comments]
      (migrate-bill-comment c))
    (doseq [s scores]
      (migrate-bill-comment-score s))))

(defn- migrate-user-votes
  "Copy all user votes to sql user_votes table"
  []
  (let [votes (dv/retrieve-all-user-votes)]
    (doseq [v votes]
      (migrate-user-vote v))))

(defn migrate-all-data
  "Migrate all data to SQL database."
  []
  (migrate-users))
