(ns com.pav.user.api.dynamodb.user
  (:require [taoensso.faraday :as far]
            [environ.core :refer [env]]
            [clojure.tools.logging :as log]
            [com.pav.user.api.domain.user :refer [convert-to-correct-profile-type]])
  (:import [java.util Date]))

(def client-opts {:access-key (:access-key env)
                  :secret-key (:secret-key env)
                  :endpoint (:dynamo-endpoint env)})

(def user-table-name (:dynamo-user-table-name env))
(def user-confirm-table-name (:dynamo-user-confirmation-table-name env))
(def notification-table-name (:dynamo-notification-table-name env))
(def userfeed-table-name (:dynamo-userfeed-table-name env))
(def timeline-table-name (:dynamo-usertimeline-table-name env))
(def follower-table-name (:dynamo-follower-table-name env))
(def following-table-name (:dynamo-following-table-name env))
(def comment-details-table-name (:dynamo-comment-details-table-name env))
(def vote-count-table-name (:dynamo-vote-count-table env))
(def question-table-name (:dynamo-questions-table env))
(def user-question-answers-table-name (:dynamo-user-question-answers-table env))

(defn get-user-by-id [id]
  (try
    (-> (far/get-item client-opts user-table-name {:user_id id})
        convert-to-correct-profile-type)
    (catch Exception e (log/info (str "Error occured retrieving user by id " e)))))

(defn get-user-by-email [email]
  (try
    (-> (first (far/query client-opts user-table-name {:email [:eq email]} {:index "user-email-idx"}))
        convert-to-correct-profile-type)
    (catch Exception e (log/info (str "Error occured retrieving user by email " e)))))

(defn get-user-profile-by-facebook-id [facebook_id]
	(try
		(-> (first (far/query client-opts user-table-name {:facebook_id [:eq facebook_id]} {:index "fbid-idx"}))
			convert-to-correct-profile-type)
		(catch Exception e (log/info (str "Error occured retrieving user by email " e)))))

(defn create-confirmation-record [user_id token]
  (far/put-item client-opts user-confirm-table-name {:user_id user_id
                                                     :confirmation-token token}))
(defn create-user [user-profile]
  (try
   (far/put-item client-opts user-table-name user-profile)
   (create-confirmation-record (:user_id user-profile) (:confirmation-token user-profile))
   user-profile
   (catch Exception e (log/info (str "Error occured persisting new user-profile " e " to table " user-table-name)))))

(defn delete-user [user_id]
	(try
		(far/delete-item client-opts user-table-name {:user_id user_id})
	(catch Exception e (log/info "Error occured deleting user " user_id) e)))

(defn update-user-token [user_id new-token]
  (try
   (far/update-item client-opts user-table-name {:user_id user_id} {:token [:put (:token new-token)]})
  (catch Exception e (log/info (str "Error occured updating user token " e)))))

(defn update-facebook-user-token [user_id new-facebook-token new-token]
  (try
   (far/update-item client-opts user-table-name {:user_id user_id} {:token          [:put (:token new-token)]
                                                                    :facebook_token [:put new-facebook-token]})
  (catch Exception e (log/info (str "Error occured updating user token " e)))))

(defn get-confirmation-token [token]
  (far/get-item client-opts user-confirm-table-name {:confirmation-token token}))

(defn update-registration [token]
  (try
    (let [{user_id :user_id} (get-confirmation-token token)]
      (if-not (empty? user_id)
        (far/update-item client-opts user-table-name {:user_id user_id} {:registered [:put true]})))
  (catch Exception e (log/info (str "Error occured updating registeration status for token " token " " e)))))

(defn get-notifications [user_id]
	{:next-page 0
	 :results (far/query client-opts notification-table-name {:user_id [:eq user_id]} {:order :desc :limit 10})})

(defn mark-notification [id]
	(let [notification (first
											 (far/query client-opts notification-table-name {:notification_id [:eq id]}
												{:index "notification_id-idx" :return [:user_id :timestamp]}))]
		(when notification
			(far/update-item client-opts notification-table-name notification {:read [:put true]}))))

(defn get-user-timeline [user_id]
	{:next-page 0
	 :results (far/query client-opts timeline-table-name {:user_id [:eq user_id]} {:order :desc :limit 10})})

(defn add-bill-comment-count [{:keys [bill_id] :as event}]
	(let [ccount (count (far/query client-opts comment-details-table-name {:bill_id [:eq bill_id]} {:index "bill-comment-idx"}))]
		(assoc event :comment_count ccount)))

(defn add-bill-vote-count [{:keys [bill_id] :as event}]
	(let [vcount (far/get-item client-opts vote-count-table-name {:bill_id bill_id}
								 {:return [:yes-count :no-count]})]
		(if vcount
			(merge event vcount)
			(merge event {:yes-count 0 :no-count 0}))))

(defn get-user-feed [user_id]
	(let [empty-result-response {:next-page 0 :results []}
        feed (far/query client-opts userfeed-table-name {:user_id [:eq user_id]} {:limit 10 :order :desc})]
		(if (empty? feed)
			empty-result-response
			(assoc empty-result-response :results (->> feed
																								 (mapv add-bill-comment-count)
																								 (mapv add-bill-vote-count))))))

(defn persist-to-newsfeed [events]
	(when events
		(log/info "Events being persisted to users newsfeed " events)
		(doseq [evt events]
			(far/put-item client-opts userfeed-table-name (assoc evt :timestamp (.getTime (Date.)))))))

(defn build-follow-profile [profile]
	(when profile
		{:user_id    (:user_id profile)
		 :first_name (:first_name profile)
		 :last_name  (:last_name profile)
		 :img_url    (:img_url profile)}))

(defn retrieve-following-profile [following-info]
  (-> (far/get-item client-opts user-table-name {:user_id (:following following-info)})
      build-follow-profile))

(defn retrieve-follower-profile [follower-info]
  (-> (far/get-item client-opts user-table-name {:user_id (:follower follower-info)})
      build-follow-profile))

(defn follow-user [follower following]
  (let [created_at (.getTime (Date.))
        following-record {:user_id follower :following following :timestamp created_at}
        follower-record {:user_id following :follower follower :timestamp created_at}]
    (far/put-item client-opts following-table-name following-record)
    (far/put-item client-opts follower-table-name follower-record)))

(defn unfollow-user [follower following]
  (far/delete-item client-opts following-table-name {:user_id follower :following following})
  (far/delete-item client-opts follower-table-name {:user_id following :follower follower}))

(defn user-following [user_id]
  (->> (far/query client-opts following-table-name {:user_id [:eq user_id]})
       (map #(retrieve-following-profile %))
		   (remove nil?)))

(defn user-followers [user_id]
  (->> (far/query client-opts follower-table-name {:user_id [:eq user_id]})
       (map #(retrieve-follower-profile %))

		   (remove nil?)))

(defn following? [follower following]
  (if (empty? (far/get-item client-opts following-table-name {:user_id follower :following following}))
    false
    true))

(defn count-followers [user_id]
  (count (far/query client-opts follower-table-name {:user_id [:eq user_id]})))

(defn count-following [user_id]
  (count (far/query client-opts following-table-name {:user_id [:eq user_id]})))

(defn update-user-password [user_id password]
  (far/update-item client-opts user-table-name {:user_id user_id}
    {:password [:put password]}))

(defn update-account-settings [user_id param-map]
	(far/update-item client-opts user-table-name {:user_id user_id}
		(into {}
			(for [[k v] param-map]
				[k [:put v]]))))

(defn assign-facebook-id [user_id facebook_id]
	(far/update-item client-opts user-table-name {:user_id user_id}
		{:facebook_id [:put facebook_id]}))

(defn create-question [question]
	(far/put-item client-opts question-table-name question))

(defn retrieve-questions-by-topics [topics]
	(far/query client-opts question-table-name {:topic [:eq topics]}))

(defn submit-answers [answers]
	(far/batch-write-item client-opts
		{user-question-answers-table-name {:put answers}}))