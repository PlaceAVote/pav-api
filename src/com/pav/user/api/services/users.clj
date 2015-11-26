(ns com.pav.user.api.services.users
  (:require [environ.core :refer [env]]
            [buddy.hashers :as h]
            [com.pav.user.api.schema.user :refer [validate validate-login construct-error-msg]]
            [com.pav.user.api.dynamodb.user :as dynamo-dao]
            [com.pav.user.api.redis.redis :as redis-dao]
            [com.pav.user.api.elasticsearch.user :refer [index-user]]
            [com.pav.user.api.authentication.authentication :refer [token-valid?]]
            [com.pav.user.api.mandril.mandril :refer [send-confirmation-email]]
            [buddy.sign.jws :as jws]
            [buddy.sign.util :as u]
            [buddy.core.keys :as ks]
            [clj-time.core :as t]
            [clojure.tools.logging :as log]
            [clojure.core.async :refer [thread]])
  (:import [java.util Date UUID]))

(defn- pkey []
  (ks/private-key (:auth-priv-key env) (:auth-priv-key-pwd env)))

(defn create-auth-token [user]
  {:token (jws/sign user (pkey)
                    {:alg :rs256
                     :exp (-> (t/plus (t/now) (t/days 30)) (u/to-timestamp))})})

(defn assoc-new-token [user]
  (let [safe-profile (dissoc user :token)
        new-token (create-auth-token safe-profile)]
    (merge safe-profile new-token)))

(defn assoc-common-attributes [user]
  (-> user
      (assoc :user_id (.toString (UUID/randomUUID)))
      (assoc :created_at (.getTime (Date.)))
      (assoc :confirmation-token (.toString (UUID/randomUUID)))
      (merge {:registered false :public false})))

(defn create-user [user-profile]
  (dynamo-dao/create-user user-profile)
  (redis-dao/create-user-profile user-profile)
  user-profile)

(defn create-facebook-user [user]
  (log/info (str "Creating user " user " from facebook"))
  (let [user-with-token (-> (assoc-common-attributes user)
                            (assoc :facebook_token (:token user))
                            assoc-new-token)
        new-user-record (-> (create-user user-with-token)
                            (dissoc :facebook_token :confirmation-token))]
    (index-user (dissoc new-user-record :token))
    (send-confirmation-email user-with-token)
    {:record new-user-record}))

(defn create-pav-user [user]
  (log/info (str "Creating user " user))
  (let [user-with-token (-> (update-in user [:password] #(h/encrypt %))
                            assoc-common-attributes
                            assoc-new-token)
        new-user-record (-> (create-user user-with-token)
                            (dissoc :password :confirmation-token))]
    (index-user (dissoc new-user-record :token))
    (send-confirmation-email user-with-token)
    {:record new-user-record}))

(defn remove-sensitive-information [user]
  (if user
    (dissoc user :password :confirmation-token)))

(defn get-user-by-id [id]
  (-> (or (redis-dao/get-user-profile id)
          (dynamo-dao/get-user-by-id id))
      remove-sensitive-information))

(defn get-user-by-email [email]
  (-> (or (redis-dao/get-user-profile-by-email email)
          (dynamo-dao/get-user-by-email email))
      remove-sensitive-information))

(defn update-user-token [{:keys [email token]} origin]
  "Take current users email and token and update these values in databases.  Token can only be passed for facebook
  authentications"
  (let [{:keys [user_id] :as current-user} (get-user-by-email email)
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
  (if (empty? (get-user-by-email (get-in user [:email])))
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

(defn get-timeline [user from]
  (let [timeline (redis-dao/get-user-timeline user from)]
    (if (empty? timeline)
      (dynamo-dao/get-user-timeline user)
      timeline)))

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

(defn user-following [user_id]
  (dynamo-dao/user-following user_id))

(defn user-followers [user_id]
  (dynamo-dao/user-followers user_id))

(defn count-followers [user_id]
  (dynamo-dao/count-followers user_id))

(defn count-following [user_id]
  (dynamo-dao/count-following user_id))

(defn get-user-profile
  ([user_id]
    (-> (get-user-by-id user_id)
        (dissoc :email :token :topics)
        (assoc :total_followers (count-followers user_id))
        (assoc :total_following (count-following user_id))))
  ([current-user user_id]
    (-> (get-user-profile user_id)
        (assoc :following (following? current-user user_id)))))

(defn validate-token [token]
  (token-valid? token))