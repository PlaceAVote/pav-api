(ns com.pav.user.api.services.users
  (:require [buddy.hashers :as h]
            [com.pav.user.api.schema.user :as us]
            [com.pav.user.api.dynamodb.user :as dynamo-dao]
            [com.pav.user.api.redis.redis :as redis-dao]
            [com.pav.user.api.elasticsearch.user :refer [index-user gather-latest-bills-by-subject]]
            [com.pav.user.api.authentication.authentication :refer [token-valid? create-auth-token]]
            [com.pav.user.api.mandril.mandril :refer [send-confirmation-email send-password-reset-email]]
            [com.pav.user.api.domain.user :refer [new-user-profile presentable profile-info create-token-for
																									account-settings indexable-profile]]
            [clojure.core.async :refer [thread]]
            [clojure.tools.logging :as log]
						[clojure.core.memoize :as memo])
  (:import (java.util Date UUID)))

(def gather-cached-bills
	"Retrieve cached bills that match previous topic arguments.  For performance purposes."
	(memo/ttl gather-latest-bills-by-subject :ttl/threshold 3600000))

(defn- pre-populate-newsfeed [{:keys [user_id topics]}]
	(let [bills (gather-cached-bills topics)]
		(if-not (empty? bills)
			(dynamo-dao/persist-to-newsfeed
				(mapv #(assoc % :event_id (.toString (UUID/randomUUID))
												:user_id user_id) bills)))))


(defn- persist-user-profile [{:keys [user_id] :as profile}]
  "Create new user profile profile to dynamo and redis."
	(when profile
		(try
			(dynamo-dao/create-user profile)
			(redis-dao/create-user-profile profile)
      (pre-populate-newsfeed profile)
			(thread ;; Expensive call to mandril.  Execute in seperate thread.
				(index-user (indexable-profile profile))
				(send-confirmation-email profile))
		(catch Exception e (log/error (str "Error occured persisting user profile for " user_id "Exception: " e)))))
  profile)

(defn create-user-profile [user & [origin]]
  "Create new user profile, specify :facebook as the origin by default all uses are pav"
	{:record (-> (new-user-profile user (or origin :pav)) persist-user-profile (select-keys [:token :user_id]))})

(defn delete-user [{:keys [user_id] :as user_profile}]
	(dynamo-dao/delete-user user_id)
	(redis-dao/delete-user-profile user_profile))

(defn- get-user-by-id [user_id]
	"Retrieve user profile from cache.  If this fails then retrieve from dynamo and populate cache"
	(let [user-from-redis (redis-dao/get-user-profile user_id)]
		(if user-from-redis
			user-from-redis
			(let [user-from-dynamodb (dynamo-dao/get-user-by-id user_id)]
				(when user-from-dynamodb
					(redis-dao/create-user-profile user-from-dynamodb)
					user-from-dynamodb)))))

(defn- get-user-by-email [email]
	"Retrieve user profile from cache.  If this fails then retrieve from dynamo and populate cache"
	(let [user-from-redis (redis-dao/get-user-profile-by-email email)]
		(if user-from-redis
			user-from-redis
			(let [user-from-dynamodb (dynamo-dao/get-user-by-email email)]
				(when user-from-dynamodb
					(redis-dao/create-user-profile user-from-dynamodb)
					user-from-dynamodb)))))

(defn- get-user-by-facebook-id [facebook_id]
	"Retrieve user profile from cache.  If this fails then retrieve from dynamo and populate cache"
	(let [user-from-redis (redis-dao/get-user-profile-by-facebook-id facebook_id)]
		(if user-from-redis
			user-from-redis
			(let [user-from-dynamodb (dynamo-dao/get-user-profile-by-facebook-id facebook_id)]
				(when user-from-dynamodb
					(redis-dao/create-user-profile user-from-dynamodb)
					user-from-dynamodb)))))

(defn update-user-token [{:keys [email token id]} origin]
  "Take current users email and token and update these values in databases.  Token can only be passed for facebook
  authentications"
  (let [{:keys [user_id facebook_id] :as current-user} (case origin
																						 :facebook (or (get-user-by-facebook-id id)
																												   (get-user-by-email email))
																						 :pav (get-user-by-email email))
        new-token (create-token-for current-user)]
    (case origin
      :pav      (do (redis-dao/update-token user_id new-token)
                    (dynamo-dao/update-user-token user_id new-token))
      :facebook (do (redis-dao/update-facebook-token user_id token new-token)
                    (dynamo-dao/update-facebook-user-token user_id token new-token)
										(if (nil? facebook_id)
											(do (dynamo-dao/assign-facebook-id user_id id)
													(redis-dao/assign-facebook-id user_id id)))))
    new-token))

(defn wrap-validation-errors [result]
	"Wrap validation errors or return nil"
	(if (seq result)
		{:errors (us/construct-error-msg result)}))

(defn validate-payload
	"Validate payload with given validator fn.  Specify Optional Origin of request if needed."
	([payload fn origin]
	 (wrap-validation-errors (fn payload origin)))
	([payload fn]
	 (wrap-validation-errors (fn payload))))

(defn validate-new-user-payload [user origin]
	(validate-payload user us/validate-new-user-payload origin))

(defn validate-user-login-payload [user origin]
	(validate-payload user us/validate-login-payload origin))

(defn validate-settings-update-payload [payload]
	(validate-payload payload us/validate-settings-payload))

(defn validate-password-change-payload [payload]
	(validate-payload payload us/validate-change-password-payload))

(defn validate-password-reset-confirmation-payload [payload]
	(validate-payload payload us/validate-confirm-reset-password-payload))

(defn facebook-user-exists? [email facebook_id]
	"Function to aid migration for existing facebook users without a facebook ID."
	(let [facebook-user (get-user-by-facebook-id facebook_id)]
		(if (seq facebook-user)
			true
			(not (empty? (get-user-by-email email))))))

(defn user-exist? [{email :email facebook_id :id}]
	"Check if user exists using there email or facebook ID"
	(if facebook_id
		(facebook-user-exists? email facebook_id)
		(not (empty? (get-user-by-email email)))))

(defn allowed-to-reset-password? [email]
	(let [user (get-user-by-email email)]
		(if user
			(not (contains? user :facebook_id))
			false)))

(defn check-pwd [attempt encrypted]
	(h/check attempt encrypted))

(defn password-matches? [user_id attempt]
	"Does the users password match the given password?"
	(let [{encrypted :password} (get-user-by-id user_id)]
		(check-pwd attempt encrypted)))

(defn valid-user? [user origin]
  (case origin
    :pav (check-pwd (:password user) (:password (dynamo-dao/get-user-by-email (:email user))))
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
    (-> (get-user-by-id user_id)
        profile-info
        (assoc :total_followers (count-followers user_id))
        (assoc :total_following (count-following user_id))))
  ([current-user user_id]
    (-> (get-user-profile user_id)
        (assoc :following (following? current-user user_id)))))

(defn- update-user-profile [user_id param-map]
	(-> (get-user-by-id user_id)
			(merge param-map)
			indexable-profile
			index-user))

(defn update-account-settings [user_id param-map]
	(when (seq param-map)
		(dynamo-dao/update-account-settings user_id param-map)
		(redis-dao/update-account-settings user_id param-map)
		(update-user-profile user_id param-map)))

(defn get-account-settings [user_id]
	(-> (get-user-by-id user_id)
			account-settings))

(defn validate-token [token]
  (token-valid? token))

(defn update-user-password [user_id new-password]
	(let [hashed-pwd (h/encrypt new-password)]
		(dynamo-dao/update-user-password user_id hashed-pwd)
		(redis-dao/update-user-password user_id hashed-pwd)))

(defn issue-password-reset-request [email]
	(let [user (get-user-by-email email)
				reset-token (.toString (UUID/randomUUID))]
		(when user
			(send-password-reset-email user reset-token)
			(redis-dao/create-password-reset-token (:email user) reset-token))))

(defn confirm-password-reset [reset-token new-password]
	(let [{user_id :user_id email :email} (get-user-by-email (redis-dao/retrieve-useremail-by-reset-token reset-token))]
		(when user_id
			(update-user-password user_id new-password)
			(redis-dao/delete-password-reset-token email reset-token))))

(defn change-password [user_id new-password]
	(when user_id
		(update-user-password user_id new-password)))