(ns com.pav.user.api.dynamodb.user
  (:require [taoensso.faraday :as far]
            [environ.core :refer [env]]
            [clojure.tools.logging :as log]
            [com.pav.user.api.domain.user :refer [convert-to-correct-profile-type]]
            [com.pav.user.api.dynamodb.db :as dy :refer [client-opts]]
            [com.pav.user.api.notifications.ws-handler :refer [publish-notification]]
            [com.pav.user.api.utils.utils :refer [uuid->base64Str
                                                  base64->uuidStr]])
  (:import [java.util Date UUID]))

(defn get-user-by-id [id]
  (try
    (-> (far/get-item client-opts dy/user-table-name {:user_id id})
        convert-to-correct-profile-type)
    (catch Exception e (log/info (str "Error occured retrieving user by id " e)))))

(defn get-user-by-email [email]
  (try
    (-> (first (far/query client-opts dy/user-table-name {:email [:eq email]} {:index "user-email-idx"}))
        convert-to-correct-profile-type)
    (catch Exception e (log/info (str "Error occured retrieving user by email " e)))))

(defn get-user-profile-by-facebook-id [facebook_id]
  (try
    (-> (first (far/query client-opts dy/user-table-name {:facebook_id [:eq facebook_id]} {:index "fbid-idx"}))
        convert-to-correct-profile-type)
    (catch Exception e (log/info (str "Error occured retrieving user by email " e)))))

(defn create-confirmation-record [user_id token]
  (far/put-item client-opts dy/user-confirm-table-name {:user_id user_id
                                                     :confirmation-token token}))
(defn create-user [user-profile]
  (try
    (far/put-item client-opts dy/user-table-name user-profile)
    (create-confirmation-record (:user_id user-profile) (:confirmation-token user-profile))
    user-profile
    (catch Exception e
      (log/errorf e "Error occured persisting new user-profile to table '%s'" dy/user-table-name))))

(defn delete-user [user_id]
  (try
    (far/delete-item client-opts dy/user-table-name {:user_id user_id})
    (catch Exception e
      (log/errorf e "Error occured deleting user '%s'" user_id))))

(defn update-user-token [user_id new-token]
  (try
    (far/update-item client-opts dy/user-table-name {:user_id user_id}
      {:update-map {:token [:put (:token new-token)]}})
    (catch Exception e (log/info (str "Error occured updating user token " e)))))

(defn update-facebook-user-token [user_id new-facebook-token new-token]
  (try
    (far/update-item client-opts dy/user-table-name {:user_id user_id}
      {:update-map {:token          [:put (:token new-token)]
                    :facebook_token [:put new-facebook-token]}})
    (catch Exception e (log/info (str "Error occured updating user token " e)))))

(defn get-confirmation-token [token]
  (far/get-item client-opts dy/user-confirm-table-name {:confirmation-token token}))

(defn update-registration [token]
  (try
    (let [{user_id :user_id} (get-confirmation-token token)]
      (if-not (empty? user_id)
        (far/update-item client-opts dy/user-table-name {:user_id user_id}
          {:update-map {:registered [:put true]}})))
    (catch Exception e (log/info (str "Error occured updating registeration status for token " token " " e)))))

(defn wrap-query-with-pagination
  "Helper function to wrap common pagination functionality for timeline related data."
  [key-conds opts table]
  (if-let [results (far/query client-opts table key-conds opts)]
    {:last_timestamp (:timestamp (last results)) :results results}
    {:last_timestamp 0 :results []}))

(defn get-notifications [user_id & [from]]
  (let [opts (merge
               {:limit 10 :span-reqs {:max 1} :order :desc}
               (if from {:last-prim-kvs {:user_id user_id :timestamp (read-string from)}}))]
    (wrap-query-with-pagination {:user_id [:eq user_id]} opts dy/notification-table-name)))

(defn mark-notification [id]
  (let [notification (first
                      (far/query client-opts dy/notification-table-name {:notification_id [:eq id]}
                                 {:index "notification_id-idx" :return [:user_id :timestamp]}))]
    (when notification
      (far/update-item client-opts dy/notification-table-name notification
        {:update-map {:read [:put true]}}))))

(declare event-meta-data)
(defn get-user-timeline [user_id & [from]]
  (let [opts (merge
               {:limit 10 :span-reqs {:max 1} :order :desc}
               (if from {:last-prim-kvs {:user_id user_id :timestamp (read-string from)}}))]
    (-> (wrap-query-with-pagination {:user_id [:eq user_id]} opts dy/timeline-table-name)
        (update-in [:results] (fn [results]
                                (mapv #(event-meta-data % user_id) results))))))

(defn add-bill-comment-count [{:keys [bill_id] :as feed-event}]
  "Count Bill comments associated with feed event."
  (let [ccount (count (far/query client-opts dy/comment-details-table-name {:bill_id [:eq bill_id]} {:index "bill-comment-idx"}))]
    (assoc feed-event :comment_count ccount)))

(defn add-bill-vote-count [{:keys [bill_id] :as feed-event}]
  "Count Bill votes associated with feed event."
  (let [vcount (far/get-item client-opts dy/vote-count-table-name {:bill_id bill_id}
                 {:return [:yes-count :no-count]})]
    (if vcount
      (merge feed-event vcount)
      (merge feed-event {:yes-count 0 :no-count 0}))))

(defmulti event-meta-data
  "Retrieve meta data for event item by type"
  (fn [event user_id]
    (:type event)))

(defmethod event-meta-data "bill" [feed-event _]
  (-> (add-bill-comment-count feed-event)
      add-bill-vote-count))

(defmethod event-meta-data :default [feed-event _]
  feed-event)

(declare get-user-issue-emotional-response get-user-issue)
(defmethod event-meta-data "userissue" [{:keys [issue_id] :as feed-event} user_id]
  (merge feed-event
    (get-user-issue-emotional-response issue_id user_id)
    (if-let [issue (get-user-issue (:author_id feed-event) issue_id)]
      (if (empty? (:short_issue_id issue_id))
        (assoc issue :short_issue_id (uuid->base64Str (UUID/fromString (:issue_id issue))))
        issue))
    (select-keys (get-user-by-id (:author_id feed-event)) [:first_name :last_name :img_url])))

(defn get-user-feed [user_id & [from]]
  (let [opts (merge
               {:limit 10 :span-reqs {:max 1} :order :desc}
               (if from {:last-prim-kvs {:user_id user_id :timestamp (read-string from)}}))]
    (-> (wrap-query-with-pagination {:user_id [:eq user_id]} opts dy/userfeed-table-name)
        (update-in [:results] (fn [results]
                                (mapv #(event-meta-data % user_id) results))))))

(defn persist-to-newsfeed [events]
  (when events
    (doseq [event events]
      (log/info "Event being persisted to users newsfeed "
        (select-keys event [:event_id :user_id :timestamp :bill_id :issue_id]))
      (try
        (far/put-item client-opts dy/userfeed-table-name event)
        (catch Exception e (log/error (str "Problem occurred persisting event to new users feed: " event) e))))))

(defn build-follow-profile [profile]
  (when profile
    {:user_id    (:user_id profile)
     :first_name (:first_name profile)
     :last_name  (:last_name profile)
     :img_url    (:img_url profile)}))

(defn retrieve-following-profile [following-info]
  (-> (far/get-item client-opts dy/user-table-name {:user_id (:following following-info)})
      build-follow-profile))

(defn retrieve-follower-profile [follower-info]
  (-> (far/get-item client-opts dy/user-table-name {:user_id (:follower follower-info)})
      build-follow-profile))

(defn follow-user [follower following]
  (let [created_at (.getTime (Date.))
        following-record {:user_id follower :following following :timestamp created_at}
        follower-record {:user_id following :follower follower :timestamp created_at}]
    (far/put-item client-opts dy/following-table-name following-record)
    (far/put-item client-opts dy/follower-table-name follower-record)))

(defn unfollow-user [follower following]
  (far/delete-item client-opts dy/following-table-name {:user_id follower :following following})
  (far/delete-item client-opts dy/follower-table-name {:user_id following :follower follower}))

(defn user-following [user_id]
  (->> (far/query client-opts dy/following-table-name {:user_id [:eq user_id]})
       (map #(retrieve-following-profile %))
       (remove nil?)))

(defn user-followers [user_id]
  (->> (far/query client-opts dy/follower-table-name {:user_id [:eq user_id]})
       (map #(retrieve-follower-profile %))
       (remove nil?)))

(defn following? [follower following]
  (if (empty? (far/get-item client-opts dy/following-table-name {:user_id follower :following following}))
    false
    true))

(defn count-followers [user_id]
  (count (far/query client-opts dy/follower-table-name {:user_id [:eq user_id]})))

(defn count-following [user_id]
  (count (far/query client-opts dy/following-table-name {:user_id [:eq user_id]})))

(defn update-user-password [user_id password]
  (far/update-item client-opts dy/user-table-name {:user_id user_id}
    {:update-map {:password [:put password]}}))

(defn update-account-settings [user_id param-map]
  (far/update-item client-opts dy/user-table-name {:user_id user_id}
    {:update-map
     (into {}
       (for [[k v] param-map]
         [k [:put v]]))}))

(defn assign-facebook-id [user_id facebook_id]
  (far/update-item client-opts dy/user-table-name {:user_id user_id}
    {:update-map {:facebook_id [:put facebook_id]}}))

(defn bootstrap-wizard-questions [questions]
  (far/batch-write-item client-opts
                        {dy/question-table-name {:put questions}}))

(defn retrieve-questions-by-topics [topics]
  (flatten (map #(far/query client-opts dy/question-table-name {:topic [:eq %]}) topics)))

(defn submit-answers [answers]
  (far/batch-write-item client-opts
                        {dy/user-question-answers-table-name {:put answers}}))

(defn create-bill-issue
  "Create bill issues with details like user_id, bill_id and so on. Returns
new ID assigned as issue_id and timestamp stored in table."
  [details]
  (let [id (UUID/randomUUID)
        short_id (uuid->base64Str id)
        timestamp (.getTime (Date.))
        issue-data (merge {:issue_id (.toString id)
                           :short_issue_id short_id
                           :timestamp timestamp
                           :positive_responses 0
                           :neutral_responses 0
                           :negative_responses 0}
                     details)]
    (far/put-item client-opts dy/user-issues-table-name issue-data)
    issue-data))

(defn update-user-issue
  "Update user issue and return new issue."
  [user_id issue_id update-map]
  (far/update-item client-opts dy/user-issues-table-name {:issue_id issue_id :user_id user_id}
    {:update-map
     (into {}
       (for [[k v] update-map]
         [k [:put v]]))
     :return :all-new}))

(defn populate-user-and-followers-feed-table
  "Populate given user and that users followers feed when user an publishes issue."
  [user_id data]
  (let [author-event  (assoc data :user_id user_id)
        follower-evts (->> (user-followers user_id)
                           ;; Remove any followers with the same user_id.  Temporary fix to avoid the same issue we
                           ;;discovered in development
                           (remove #(= user_id (:user_id %)))
                           (map #(assoc data :user_id (:user_id %))))
        follower-and-author-evts (conj follower-evts author-event)]
    (far/put-item client-opts dy/timeline-table-name (assoc author-event :event_id (.toString (UUID/randomUUID))))
    (persist-to-newsfeed follower-and-author-evts)))

(declare delete-user-issue-emotional-response)
(defn- publish-issues-notification
  "Publish Notification to author of issue that someone has responsed to there issue."
  [notification-author user user-issue response]
  (let [notification (merge
                       {:author notification-author :user_id (:user_id user-issue) :timestamp (.getTime (Date.))
                        :type   "issueresponse" :notification_id (.toString (UUID/randomUUID))} ;;author and notification user ids
                       (select-keys user [:first_name :last_name]) ;;author names
                       (select-keys user-issue [:bill_id :bill_title]) ;; bill issue is related to
                       {:emotional_response response})]
    (far/put-item client-opts dy/notification-table-name notification)
    (publish-notification notification)))

(defn update-user-issue-emotional-response [issue_id user_id response]
  (delete-user-issue-emotional-response issue_id user_id)
  (let [user       (get-user-by-id user_id)
        user-issue (get-user-issue issue_id)
        issue-author  (:user_id user-issue)
        count-payload (case response
                        "positive" {:positive_responses [:add 1]}
                        "neutral" {:neutral_responses [:add 1]}
                        "negative" {:negative_responses [:add 1]})]
    ;;update users emotional response
    (far/update-item client-opts dy/user-issue-responses-table-name {:issue_id issue_id :user_id user_id}
      {:update-map {:emotional_response [:put response]}})
    ;; update issue count
    (far/update-item client-opts dy/user-issues-table-name {:issue_id issue_id :user_id issue-author}
      {:update-map count-payload})
    ;; notify author of response
    (when-not (= user_id (:user_id user-issue))
      (publish-issues-notification user_id user user-issue response))))

(defn get-user-issue-emotional-response [issue_id user_id]
  (try
    (if-let [response (far/get-item client-opts dy/user-issue-responses-table-name {:issue_id issue_id :user_id  user_id})]
      response
      {:emotional_response "none"})
    (catch Exception e
      (log/errorf e "Error occured while getting emotional_response for '%s:%s'" issue_id user_id))))

(defn delete-user-issue-emotional-response [issue_id user_id]
  (when-let [current-response (get-user-issue-emotional-response issue_id user_id)]
    (let [{author_id :user_id} (get-user-issue issue_id)
          count-payload (case (:emotional_response current-response)
                          "positive"  {:cond-expr "#a > :z" :update-expr "ADD #a :n"
                                       :expr-attr-names {"#a" "positive_responses"} :expr-attr-vals {":n" -1 ":z" 0}}
                          "neutral"   {:cond-expr "#a > :z" :update-expr "ADD #a :n"
                                       :expr-attr-names {"#a" "neutral_responses"} :expr-attr-vals {":n" -1 ":z" 0}}
                          "negative"  {:cond-expr "#a > :z" :update-expr "ADD #a :n"
                                       :expr-attr-names {"#a" "negative_responses"} :expr-attr-vals {":n" -1 ":z" 0}}
                          nil)]
      (when current-response
        (far/delete-item client-opts dy/user-issue-responses-table-name {:issue_id issue_id :user_id user_id})
        (if count-payload
          (far/update-item client-opts dy/user-issues-table-name {:issue_id issue_id :user_id author_id} count-payload))))))

(defn get-user-issue
  ([issue_id] (first (far/query client-opts dy/user-issues-table-name {:issue_id [:eq issue_id]})))
  ([user_id issue_id] (far/get-item client-opts dy/user-issues-table-name {:issue_id issue_id :user_id user_id})))

(defn get-issues-by-user [user_id limit]
  (far/query client-opts dy/user-issues-table-name {:user_id [:eq user_id]}
    {:index "user-issues-idx" :limit limit :span-reqs {:max 1}}))

(defn retrieve-all-user-records
  "Performs full table scan and retrieves all user records"
  []
  (loop [user-records (far/scan client-opts dy/user-table-name)
         acc []]
    (if (:last-prim-kvs (meta user-records))
      (recur (far/scan client-opts dy/user-table-name {:last-prim-kvs (:last-prim-kvs (meta user-records))})
        (into acc user-records))
      (into acc user-records))))
