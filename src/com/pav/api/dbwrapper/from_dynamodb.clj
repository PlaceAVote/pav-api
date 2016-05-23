(ns com.pav.api.dbwrapper.from-dynamodb
  "Utility functions to move data from DynamoDB to SQL database."
  (:require [com.pav.api.dynamodb.user :as du]
            [com.pav.api.db.user :as su]
            [com.pav.api.dbwrapper.user :refer [convert-user-profile]]
            [com.pav.api.dbwrapper.helpers :refer [bigint->long]]
            [com.pav.api.dynamodb.comments :as dc]
            [com.pav.api.db.comment :as sc]
            [com.pav.api.dbwrapper.comment :refer [dynamodb->sql-comment dynamo-comment-score->sql-comment-score]]
            [com.pav.api.dynamodb.votes :as dv]
            [com.pav.api.dynamodb.db :as dy :refer [client-opts]]
            [com.pav.api.db.vote :as sv]
            [com.pav.api.dbwrapper.vote :refer [dynamo-vote->sql-vote]]
            [com.pav.api.db.issue :as si]
            [com.pav.api.db.followers :as uf]
            [com.pav.api.dbwrapper.issue :refer [dynamo-issue->sql dynamo-issueres->sql-issueres]]
            [com.pav.api.db.db :as db]
            [clojure.tools.logging :as log]
            [taoensso.faraday :as far]))

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
  (if-let [found (sc/get-comment-by-old-id (:comment_id comment))]
    (log/infof "Skipping comment found using new id '%s'" (:id found))
    (try
      (log/infof "Migrating comment '%s'" (:comment_id comment))
      (-> comment dynamodb->sql-comment sc/create-bill-comment)
      (catch Throwable e
        (log/errorf "Failed for comment '%s' with %s" (:comment_id comment) (.getMessage e))))))

(defn migrate-bill-comment-score
  "Copy single bill comment scoring record from dynamodb to sql table"
  [{old_comment_id :comment_id old_user_id :user_id :as score}]
  (if-let [found (sc/get-bill-comment-score-by-old-ids old_comment_id old_user_id)]
    (log/infof "Skipping comment scoring record found using old_user_id '%s' and old_comment_id '%s'"
      (:user_id found) (:id found))
    (try
      (log/infof "Migrating bill comment score for user '%s' and comment '%s'" old_user_id old_comment_id)
      (let [{existing-user :user_id} (su/get-user-by-old-id old_user_id)
            {existing-comment :id} (sc/get-comment-by-old-id old_comment_id)]
        (if (and existing-user existing-comment)
          (->> score dynamo-comment-score->sql-comment-score (sc/insert-user-comment-scoring-record db/db))
          (log/infof "Could not find existing comment '%s' and user '%s' in user_comment_scores table "
            old_comment_id old_user_id)))
      (catch Throwable e
        (log/errorf "Failed for bill comment scoring record for user '%s' and comment '%s' with %s"
          old_user_id old_comment_id (.getMessage e))))))

(defn- migrate-users
  "Copy all dynamodb users to sql user table."
  []
  (log/info "MIGRATION USERS")
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
      (log/warnf "Could not find existing user '%s' please migrate user records first" (:user_id v)))))

(defn- migrate-user-issue [{old_issue_id :issue_id old_user_id :user_id :as i}]
  (if (si/get-user-issue-by-old-id old_issue_id)
    (log/infof "Skipping user issue record found using old_issue_id '%s'" old_issue_id)
    (if (su/get-user-by-old-id old_user_id)
      (try
        (do
         (log/infof "Migrating user issue '%s'" old_issue_id)
         (-> i dynamo-issue->sql si/create-user-issue))
        (catch Throwable e
          (log/errorf "Failed migrating user issue for issue-id '%s' with %s" old_issue_id (.getMessage e))))
      (log/warnf "Could not find existing user '%s', please migrate user records first" old_user_id))))

(defn- migrate-user-issue-response [{old_issue_id :issue_id old_user_id :user_id :as ir}]
  (if (si/get-user-issue-response-by-old-ids old_issue_id old_user_id)
    (log/infof "Skipping user issue response record found using old_issue_id '%s' and old_user_id" old_issue_id old_user_id)
    (if (su/get-user-by-old-id old_user_id)
      (try
        (do
         (log/infof "Migrating user issue response '%s'" old_issue_id)
         (-> ir dynamo-issueres->sql-issueres si/create-user-issue-response))
        (catch Throwable e
          (log/errorf "Failed migrating user issue response for issue-id '%s' with %s" old_issue_id (.getMessage e))))
      (log/warnf "Could not find existing user '%s', please migrate user records first" old_user_id))))

(defn- migrate-bill-comments
  "Copy all dynamodb bill comments to sql user_bill_comments and comments table"
  []
  (log/info "MIGRATION BILL COMMENTS AND SCORES")
  (let [comments (dc/retrieve-all-bill-comments)
        scores (dc/retrieve-all-bill-comment-scores)]
    (doseq [c comments]
      (migrate-bill-comment c))
    (doseq [s scores]
      (migrate-bill-comment-score s))))

(defn- migrate-user-votes
  "Copy all user votes to sql user_votes table"
  []
  (log/info "MIGRATION USER VOTES")
  (let [votes (dv/retrieve-all-user-votes)]
    (doseq [v votes]
      (migrate-user-vote v))))

(defn migrate-user-issues
  "Copy all user issue data to sql user_issues and scoring tables"
  []
  (log/info "MIGRATION USER ISSUES AND RESPONSES")
  (let [issues (du/retrieve-all-user-issues)
        issue-responses (du/retrieve-all-user-issue-responses)]
    (doseq [i issues]
      (migrate-user-issue i))
    (doseq [ir issue-responses]
      (migrate-user-issue-response ir))))

(defn- dynamo-get-followings
  "Return raw followings from dynamodb, without any preprocessing."
  [id]
  (far/query client-opts dy/following-table-name {:user_id [:eq id]}))

(defn- migrate-user-followings
  "Copy user followings."
  [user]
  (let [id (:user_id user)
        dynamo-id->sql-id #(some-> % su/get-user-by-old-id :user_id)
        sql-id (dynamo-id->sql-id id)]
    (doseq [f (dynamo-get-followings id)]
      (if-let [sql-fid (-> f :following dynamo-id->sql-id)]
        (when-not (= sql-id sql-fid)
          (uf/follow-user sql-id sql-fid (-> f :timestamp bigint->long)))
        (log/warnf "User '%s' not found in SQL database" (:following f))))))

(defn migrate-users-followings
  "Copy followings from all users."
  []
  (log/info "MIGRATION USER FOLLOWINGS")
  (doseq [u (du/retrieve-all-user-records)]
    (migrate-user-followings u)))

(defn migrate-all-data
  "Migrate all data to SQL database."
  []
  (migrate-users)
  (migrate-user-votes)
  (migrate-bill-comments)
  (migrate-user-issues)
  (migrate-users-followings))
