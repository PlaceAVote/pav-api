(ns com.pav.user.api.services.users
  (:require [environ.core :refer [env]]
            [buddy.hashers :as h]
            [com.pav.user.api.schema.user :refer [validate validate-login construct-error-msg]]
            [com.pav.user.api.dynamodb.user :as dynamo-dao]
            [com.pav.user.api.redis.redis :as redis-dao]
            [com.pav.user.api.elasticsearch.user :refer [index-user gather-latest-bills-by-subject]]
            [com.pav.user.api.authentication.authentication :refer [token-valid? create-auth-token]]
            [com.pav.user.api.mandril.mandril :refer [send-confirmation-email send-password-reset-email]]
            [com.pav.user.api.domain.user :refer [new-user-profile]]
            [clojure.core.async :refer [thread]]
            [clojure.tools.logging :as log]
						[clojure.core.memoize :as memo])
  (:import (java.util Date UUID)))

(def gather-cached-bills
	"Retrieve cached bills that match previous topic arguments.  For performance purposes."
	(memo/ttl gather-latest-bills-by-subject :ttl/threshold 3600000))

(defn pre-populate-newsfeed [{:keys [user_id topics]}]
	(let [bills (gather-cached-bills topics)]
		(if-not (empty? bills)
			(dynamo-dao/persist-to-newsfeed
				(mapv #(assoc % :event_id (.toString (UUID/randomUUID))
												:user_id user_id) bills)))))


(defn persist-user-profile [{:keys [user_id] :as profile}]
  "Create new user profile profile to dynamo and redis."
	(when profile
		(try
			(dynamo-dao/create-user profile)
			(redis-dao/create-user-profile profile)
      (pre-populate-newsfeed profile)
			(thread ;; Expensive call to mandril.  Execute in seperate thread.
				(index-user (dissoc profile :token :password))
				(send-confirmation-email profile)
				(log/info "New user created " profile))
		(catch Exception e (log/error (str "Error occured persisting user profile for " user_id "Exception: " e)))))
  profile)

(defn create-user-profile [user & [origin]]
  "Create new user profile, specify :facebook as the origin by default all uses are pav"
  (let [new-user-profile (-> (new-user-profile user (or origin :pav))
                             persist-user-profile)
        presentable-record (.presentable new-user-profile)]
    {:record presentable-record}))

(defn delete-user [{:keys [user_id] :as user_profile}]
	(dynamo-dao/delete-user user_id)
	(redis-dao/delete-user-profile user_profile))

(defn get-presentable-user-by-id [user_id]
  "Try retrieving user profile from redis, if this fails then retrieve from dynamodb and populate redis
  with user profile"
  (let [user-from-redis (redis-dao/get-user-profile user_id)]
    (if user-from-redis
      (.presentable user-from-redis)
      (let [user-from-dynamodb (dynamo-dao/get-user-by-id user_id)]
        (when user-from-dynamodb
          (redis-dao/create-user-profile user-from-dynamodb)
          (.presentable user-from-dynamodb))))))

(defn get-presentable-user-by-email [email]
  "Try retrieving user profile from redis, if this fails then retrieve from dynamodb and populate redis
  with user profile"
  (let [user-from-redis (redis-dao/get-user-profile-by-email email)]
    (if user-from-redis
      (.presentable user-from-redis)
      (let [user-from-dynamodb (dynamo-dao/get-user-by-email email)]
        (when user-from-dynamodb
          (redis-dao/create-user-profile user-from-dynamodb)
          (.presentable user-from-dynamodb))))))

(defn get-user-by-email [email]
	(let [user-from-redis (redis-dao/get-user-profile-by-email email)]
		(if user-from-redis
			user-from-redis
			(let [user-from-dynamodb (dynamo-dao/get-user-by-email email)]
				(when user-from-dynamodb
					(redis-dao/create-user-profile user-from-dynamodb)
					user-from-dynamodb)))))

(defn update-user-token [{:keys [email token]} origin]
  "Take current users email and token and update these values in databases.  Token can only be passed for facebook
  authentications"
  (let [{:keys [user_id] :as current-user} (get-presentable-user-by-email email)
        new-token (create-auth-token (dissoc current-user :password :token))]
    (case origin
      :pav      (do (redis-dao/update-token user_id new-token)
                    (dynamo-dao/update-user-token user_id new-token))
      :facebook (do (redis-dao/update-facebook-token user_id token new-token)
                    (dynamo-dao/update-facebook-user-token user_id token new-token)))
    new-token))

(defn validate-user-payload [user origin]
  (let [result (validate user origin)]
    (if-not (nil? result)
      {:errors (construct-error-msg result)})))

(defn validate-user-login [user origin]
  (let [result (validate-login user origin)]
    (if-not (nil? result)
      {:errors (construct-error-msg result)})))

(defn user-exist? [user]
  (if (empty? (get-presentable-user-by-email (get-in user [:email])))
    false
    true))

(defn check-pwd [user existing-user]
  (h/check (:password user) (:password existing-user)))

(defn valid-user? [user origin]
  (case origin
    :pav (check-pwd user (dynamo-dao/get-user-by-email (:email user)))
    :facebook (user-exist? user)))

(defn authenticate-user [user origin]
  {:record (update-user-token user origin)})

(defn is-authenticated? [user]
  (if-not (nil? (:user_id user))
    true
    false))

(defn update-registration [token]
  (dynamo-dao/update-registration token))

(defn confirm-token-valid? [token]
  (if-not (nil? (dynamo-dao/get-confirmation-token token))
    true
    false))

(defn get-notifications [user]
  (dynamo-dao/get-notifications user))

(defn mark-notification [id]
	(dynamo-dao/mark-notification id))

(defn get-timeline [user from]
	(dynamo-dao/get-user-timeline user))

(defn get-feed [user]
	(dynamo-dao/get-user-feed user))

(defn publish-to-timeline [event]
  (redis-dao/publish-to-timeline event))

(defn publish-following-event [follower following]
  (thread
    (let [following-event {:type      "followinguser" :user_id follower :following_id following
                           :timestamp (.getTime (Date.))}
         follower-event   {:type      "followedbyuser" :user_id following :follower_id follower
                           :timestamp (.getTime (Date.))}]
     (publish-to-timeline following-event)
     (publish-to-timeline follower-event))))

(defn following? [follower following]
  (dynamo-dao/following? follower following))

(defn follow-user [follower following]
  (if-not (following? follower following)
    (do (dynamo-dao/follow-user follower following)
        (publish-following-event follower following))))

(defn unfollow-user [follower following]
  (dynamo-dao/unfollow-user follower following))

(defn count-followers [user_id]
  (dynamo-dao/count-followers user_id))

(defn count-following [user_id]
  (dynamo-dao/count-following user_id))

(defn user-followers [user_id]
	(->> (dynamo-dao/user-followers user_id)
		   (mapv #(assoc % :follower_count (count-followers (:user_id %))))
			 (sort-by :follower_count >)))

(defn user-following [user_id]
	(->> (dynamo-dao/user-following user_id)
			 (mapv #(assoc % :follower_count (count-followers (:user_id %))))
		   (sort-by :follower_count >)))

(defn get-user-profile
  ([user_id]
    (-> (get-presentable-user-by-id user_id)
        (dissoc :email :token :topics)
        (assoc :total_followers (count-followers user_id))
        (assoc :total_following (count-following user_id))))
  ([current-user user_id]
    (-> (get-user-profile user_id)
        (assoc :following (following? current-user user_id)))))

(defn update-account-settings [user_id param-map]
	(dynamo-dao/update-account-settings user_id param-map)
	(redis-dao/update-account-settings user_id param-map))

(defn get-account-settings [user_id]
	(-> (get-presentable-user-by-id user_id)
		(select-keys [:public])))

(defn validate-token [token]
  (token-valid? token))

(defn update-user-password [user_id new-password]
	(let [hashed-pwd (h/encrypt new-password)]
		(dynamo-dao/update-user-password user_id hashed-pwd)
		(redis-dao/update-user-password user_id hashed-pwd)))

(defn issue-password-reset-request [email]
	(let [user (get-presentable-user-by-email email)
				reset-token (.toString (UUID/randomUUID))]
		(when user
			(send-password-reset-email user reset-token)
			(redis-dao/create-password-reset-token (:email user) reset-token))))

(defn confirm-password-reset [reset-token new-password]
	(let [{user_id :user_id email :email} (get-user-by-email (redis-dao/retrieve-useremail-by-reset-token reset-token))]
		(when user_id
			(update-user-password user_id new-password)
			(redis-dao/delete-password-reset-token email reset-token))))