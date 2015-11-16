(ns com.pav.user.api.services.users
  (:require [environ.core :refer [env]]
            [buddy.hashers :as h]
            [com.pav.user.api.schema.user :refer [validate validate-login construct-error-msg]]
            [com.pav.user.api.dynamodb.user :as dynamo-dao]
            [com.pav.user.api.redis.redis :as redis-dao]
            [com.pav.user.api.elasticsearch.user :refer [index-user]]
            [buddy.sign.jws :as jws]
            [buddy.sign.util :as u]
            [buddy.core.keys :as ks]
            [clj-time.core :as t]
            [clojure.tools.logging :as log]
            [clojure.core.async :refer [go]]
            [taoensso.faraday :as far])
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
      (merge {:registered false :public false})))

(defn create-facebook-user [user]
  (log/info (str "Creating user " user " from facebook"))
  (let [user-with-token (-> (assoc-common-attributes user)
                            (assoc :facebook_token (:token user))
                            assoc-new-token)
        new-user-record (-> (dynamo-dao/create-user user-with-token)
                            (dissoc :facebook_token))]
    (index-user (dissoc new-user-record :token))
    {:record new-user-record}))

(defn create-user [user]
  (log/info (str "Creating user " user))
  (let [user-with-token (-> (update-in user [:password] #(h/encrypt %))
                            assoc-common-attributes
                            assoc-new-token)
        new-user-record (-> (dynamo-dao/create-user user-with-token)
                            (dissoc :password))]
    (index-user (dissoc new-user-record :token))
    {:record new-user-record}))

(defn remove-sensitive-information [user]
  (if user
    (dissoc user :password)))

(defn get-user-by-id [id]
  (-> (dynamo-dao/get-user-by-id id)
      remove-sensitive-information))

(defn get-user-by-email [email]
  (-> (dynamo-dao/get-user-by-email email)
      remove-sensitive-information))

(defn update-user-token [user origin]
  (let [current-user (get-user-by-email (:email user))
        new-token (create-auth-token (dissoc current-user :password :token))]
    (case origin
      :pav (dynamo-dao/update-user-token user new-token)
      :facebook (dynamo-dao/update-facebook-user-token user new-token))))

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
  {:record (-> (update-user-token user origin)
               (select-keys [:token]))})

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

(defn get-timeline [user]
  (let [timeline (redis-dao/get-user-timeline user)]
    (if (empty? timeline)
      (dynamo-dao/get-user-timeline user)
      timeline)))

(defn publish-to-timeline [event]
  (dynamo-dao/publish-to-timeline event)
  (redis-dao/publish-to-timeline event))

(defn publish-following-event [follower following]
  (go
    (let [follower-profile (get-user-by-id follower)
         following-profile (get-user-by-id following)
         following-event {:type       "following" :user_id follower :following_id following
                          :first_name (:first_name following-profile) :last_name (:last_name following-profile)
                                      :timestamp (.getTime (Date.))}
         follower-event {:type       "follower" :user_id following :follower_id follower
                         :first_name (:first_name follower-profile) :last_name (:last_name follower-profile)
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
        (assoc :total_followers (count-followers user_id))
        (assoc :total_following (count-following user_id))))
  ([current-user user_id]
    (-> (get-user-by-id user_id)
        (assoc :following (following? current-user user_id))
        (assoc :total_followers (count-followers user_id))
        (assoc :total_following (count-following user_id)))))