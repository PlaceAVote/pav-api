(ns com.pav.user.api.dynamodb.user
  (:require [taoensso.faraday :as far]
            [environ.core :refer [env]]
            [clojure.tools.logging :as log]
            [com.pav.user.api.domain.user :refer [convert-to-correct-profile-type]]
            [com.pav.user.api.dynamodb.db :as dy :refer [client-opts]])
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
    (far/update-item client-opts dy/user-table-name {:user_id user_id} {:token [:put (:token new-token)]})
    (catch Exception e (log/info (str "Error occured updating user token " e)))))

(defn update-facebook-user-token [user_id new-facebook-token new-token]
  (try
    (far/update-item client-opts dy/user-table-name {:user_id user_id} {:token          [:put (:token new-token)]
                                                                     :facebook_token [:put new-facebook-token]})
    (catch Exception e (log/info (str "Error occured updating user token " e)))))

(defn get-confirmation-token [token]
  (far/get-item client-opts dy/user-confirm-table-name {:confirmation-token token}))

(defn update-registration [token]
  (try
    (let [{user_id :user_id} (get-confirmation-token token)]
      (if-not (empty? user_id)
        (far/update-item client-opts dy/user-table-name {:user_id user_id} {:registered [:put true]})))
    (catch Exception e (log/info (str "Error occured updating registeration status for token " token " " e)))))

(defn get-notifications [user_id]
  {:next-page 0
   :results (far/query client-opts dy/notification-table-name {:user_id [:eq user_id]} {:order :desc :limit 10})})

(defn mark-notification [id]
  (let [notification (first
                      (far/query client-opts dy/notification-table-name {:notification_id [:eq id]}
                                 {:index "notification_id-idx" :return [:user_id :timestamp]}))]
    (when notification
      (far/update-item client-opts dy/notification-table-name notification {:read [:put true]}))))

(defn get-user-timeline [user_id]
  {:next-page 0
   :results (far/query client-opts dy/timeline-table-name {:user_id [:eq user_id]} {:order :desc :limit 10})})

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

(defmulti feed-meta-data
  "Retrieve meta data for feed item by type"
  (fn [event user_id]
    (:type event)))

(defmethod feed-meta-data "bill" [feed-event _]
  (-> (add-bill-comment-count feed-event)
      add-bill-vote-count))

(declare get-user-issue-emotional-response)

(defmethod feed-meta-data "userissue" [feed-event user_id]
  (merge feed-event (get-user-issue-emotional-response (:issue_id feed-event) user_id)))

(defn get-user-feed [user_id]
  (let [empty-result-response {:next-page 0 :results []}
        feed (far/query client-opts dy/userfeed-table-name {:user_id [:eq user_id]} {:limit 10 :order :desc})]
    (if (empty? feed)
      empty-result-response
      (assoc empty-result-response :results (mapv #(feed-meta-data % user_id) feed)))))

(defn persist-to-newsfeed [events]
  (when events
    (log/info "Events being persisted to users newsfeed " events)
    (doseq [evt events]
      (far/put-item client-opts dy/userfeed-table-name (assoc evt :timestamp (.getTime (Date.)))))))

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
                   {:password [:put password]}))

(defn update-account-settings [user_id param-map]
  (far/update-item client-opts dy/user-table-name {:user_id user_id}
                   (into {}
                         (for [[k v] param-map]
                           [k [:put v]]))))

(defn assign-facebook-id [user_id facebook_id]
  (far/update-item client-opts dy/user-table-name {:user_id user_id}
                   {:facebook_id [:put facebook_id]}))

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
  (let [id (.toString (UUID/randomUUID))
        timestamp (.getTime (Date.))
        issue-data (merge {:issue_id id
                           :timestamp timestamp
                           :positive_responses 0
                           :neutral_responses 0
                           :negative_responses 0}
                     details)]
    (far/put-item client-opts dy/user-issues-table-name issue-data)
    issue-data))

(defn populate-user-and-followers-feed-table
  "Populate given user and that users followers feed when user an publishes issue."
  [user_id data]
  (let [author-event  (assoc data :user_id user_id)
        follower-evts (->> (user-followers user_id)
                           ;; Remove any followers with the same user_id.  Temporary fix to avoid the same issue we
                           ;;discovered in development
                           (remove #(= user_id (:user_id %)))
                           (map #(assoc data :user_id (:user_id %))))
        follower-evts {:put (conj follower-evts author-event)}]
    (far/batch-write-item client-opts {dy/userfeed-table-name follower-evts})))

(defn update-user-issue-emotional-response [issue_id user_id response]
  (far/update-item client-opts dy/user-issue-responses-table-name {:issue_id issue_id
                                                               :user_id user_id}
                   {:emotional_response [:put response]}))

(defn get-user-issue-emotional-response [issue_id user_id]
  (try
    (if-let [response (far/get-item client-opts dy/user-issue-responses-table-name {:issue_id issue_id
                                                                                    :user_id  user_id})]
      response
      {:emotional_response "none"})
    (catch Exception e
      (log/errorf e "Error occured while getting emotional_response for '%s:%s'" issue_id user_id))))

(defn delete-user-issue-emotional-response [issue_id user_id]
  (far/delete-item client-opts dy/user-issue-responses-table-name {:issue_id issue_id
                                                                   :user_id user_id}))

(defn get-user-issue [issue_id]
  (first (far/query client-opts dy/user-issues-table-name {:issue_id [:eq issue_id]})))
