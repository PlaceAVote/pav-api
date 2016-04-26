(ns com.pav.api.services.users
  (:require [buddy.hashers :as h]
            [com.pav.api.schema.user :as us]
            [com.pav.api.schema.comment :as cs]
            [com.pav.api.utils.utils :as utils]
            [com.pav.api.dynamodb.user :as dynamo-dao]
            [com.pav.api.redis.redis :as redis-dao]
            [com.pav.api.elasticsearch.user :refer [index-user gather-latest-bills-by-subject get-bill-info]]
            [com.pav.api.authentication.authentication :refer [token-valid? create-auth-token]]
            [com.pav.api.mandril.mandril :as mandril :refer [send-password-reset-email send-welcome-email]]
            [com.pav.api.domain.user :refer [new-user-profile presentable profile-info assign-token-for
                                                  account-settings indexable-profile]]
            [com.pav.api.graph.graph-parser :as gp]
            [com.pav.api.s3.user :as s3]
            [com.pav.api.location.location-service :as loc]
            [com.pav.api.facebook.facebook-service :as fb]
            [com.pav.api.events.handler :refer [process-event]]
            [com.pav.api.events.user :refer [create-followinguser-timeline-event]]
            [clojure.core.async :refer [thread]]
            [clojure.tools.logging :as log]
            [clojure.core.memoize :as memo]
            [taoensso.truss :refer [have]]
            [environ.core :refer [env]])
  (:import (java.util Date UUID)
           (java.sql Timestamp)))

(def ^{:doc "List of default followers to retrieve Issue related data from"}
default-followers (:default-followers env))

(def gather-cached-bills
  "Retrieve cached bills that match previous topic arguments.  For performance purposes."
  (memo/ttl gather-latest-bills-by-subject :ttl/threshold 86400))

(declare get-user-by-email get-user-by-id follow-user construct-issue-feed-object)
(defn- get-default-followers [followers]
  (when-let [follower-emails followers]
    (->> (clojure.string/split follower-emails #",")
      (map get-user-by-email)
      (remove nil?))))

(def cached-followers
  "Retrieve cached default followers for all new users."
  (memo/ttl get-default-followers :ttl/threshold 86400000))

(defn get-default-issues
  "Retrieve top 2 issue events per user"
  [followers]
  (->> (map #(dynamo-dao/get-issues-by-user (:user_id %) 2) followers)
       flatten
       (map #(construct-issue-feed-object (get-user-by-id (:user_id %)) %))))

(def cached-issues
  "Retrieve cached issues from followers"
  (memo/ttl get-default-issues :ttl/threshold 86400000))

(defn add-timestamp [payload]
  (have map? payload)
  (Thread/sleep 1) ;; This is a hack but its the only way i can gurantee the timestamps are unique
  (assoc payload :timestamp (.getTime (Timestamp. (System/currentTimeMillis)))))

(defn- pre-populate-newsfeed
  "Pre-populate user feed with bills related to chosen subjects and last two issues for each default follower."
  [{:keys [user_id topics]}]
  (let [cached-bills (map add-timestamp (gather-cached-bills topics))
        issues       (map add-timestamp (cached-issues (cached-followers default-followers)))
        feed-items   (mapv #(assoc % :event_id (.toString (UUID/randomUUID)) :user_id user_id) (into cached-bills issues))]
    (if (seq feed-items)
      (dynamo-dao/persist-to-newsfeed feed-items))))

(defn- persist-user-profile [{:keys [user_id] :as profile}]
  "Create new user profile profile to dynamo and redis."
  (when profile
    (try
      (dynamo-dao/create-user profile)
      (redis-dao/create-user-profile profile)
      (index-user (indexable-profile profile))
      (pre-populate-newsfeed profile)
      (send-welcome-email profile)
      (catch Exception e
        (log/errorf e "Error occured persisting user profile for '%s'" user_id)))
    profile))

(defn create-user-profile [user & [origin]]
  "Create new user profile, specify :facebook as the origin by default all uses are pav"
  {:record (-> (new-user-profile user (or origin :pav)) persist-user-profile (select-keys [:token :user_id]))})

(defn delete-user [{:keys [user_id] :as user_profile}]
  (dynamo-dao/delete-user user_id)
  (redis-dao/delete-user-profile user_profile))

(defn get-user-by-id [user_id]
  "Retrieve user profile from cache.  If this fails then retrieve from dynamo and populate cache"
  (if-let [user-from-redis (redis-dao/get-user-profile user_id)]
    user-from-redis
    (when-let [user-from-dynamodb (dynamo-dao/get-user-by-id user_id)]
      (redis-dao/create-user-profile user-from-dynamodb)
      user-from-dynamodb)))

(defn- get-user-by-email [email]
  "Retrieve user profile from cache.  If this fails then retrieve from dynamo and populate cache"
  (if-let [user-from-redis (redis-dao/get-user-profile-by-email email)]
    user-from-redis
    (when-let [user-from-dynamodb (dynamo-dao/get-user-by-email email)]
      (redis-dao/create-user-profile user-from-dynamodb)
      user-from-dynamodb)))

(defn- get-user-by-facebook-id [facebook_id]
  "Retrieve user profile from cache.  If this fails then retrieve from dynamo and populate cache"
  (if-let [user-from-redis (redis-dao/get-user-profile-by-facebook-id facebook_id)]
    user-from-redis
    (when-let [user-from-dynamodb (dynamo-dao/get-user-profile-by-facebook-id facebook_id)]
      (redis-dao/create-user-profile user-from-dynamodb)
      user-from-dynamodb)))

(defn update-user-token [{:keys [email token id]} origin]
  "Take current users email and token and update these values in databases.  Token can only be passed for facebook
  authentications"
  (let [{:keys [user_id facebook_id] :as current-user} (case origin
                                                         :facebook (or (get-user-by-facebook-id id)
                                                                       (get-user-by-email email))
                                                         :pav (get-user-by-email email))
        {:keys [pav-token] :as new-token} (assign-token-for current-user)]
    (case origin
      :pav      (do (redis-dao/update-token user_id pav-token)
                    (dynamo-dao/update-user-token user_id pav-token))
      :facebook (do (when-let [fb-token (fb/generate-long-lived-token token)]
                      (redis-dao/update-facebook-token user_id pav-token fb-token)
                      (dynamo-dao/update-facebook-user-token user_id pav-token fb-token))
                    (when-not facebook_id
                      (dynamo-dao/assign-facebook-id user_id id)
                      (redis-dao/assign-facebook-id user_id id))))
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

(defn validzipcode?
  "Validate a given zipcode is associated with a US State and Congressional District"
  [{:keys [zipcode]}]
  (if zipcode
    (if-let [location (loc/retrieve-location-by-zip zipcode)]
     (and (= (:country_code location) "USA")
       (contains? location :state)
       (contains? location :district))
     false)
    false))

(defn validate-new-user-payload [{:keys [email] :as user} origin]
  (log/info (str "Validating user " email " from the " origin " signup process, payload => " user))
  (if-let [errors (validate-payload user us/validate-new-user-payload origin)]
    errors
    (if-not (validzipcode? user)
      (wrap-validation-errors {:zipcode "Zipcode is not a valid US Zip+4 code."}))))

(defn validate-user-login-payload [user origin]
  (validate-payload user us/validate-login-payload origin))

(defn validate-settings-update-payload [payload]
  (validate-payload payload us/validate-settings-payload))

(defn validate-password-change-payload [payload]
  (-> (validate-payload payload us/validate-change-password-payload)
      wrap-validation-errors))

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

(defn- assoc-email-error [errors email]
  (cond-> errors
    (user-exist? email) (conj {:email "This email is currently in use."})))

(defn- validate-conflicting-user-properties
  "Validates a collection of user properties and validates them for conflicts and returns a vector of errors or Nil"
  [{:keys [email] :as b}]
  (->
    (cond-> []
      email (assoc-email-error b))))

(defn validate-user-properties [properties]
  (seq
    (remove nil?
      (->
        (validate-conflicting-user-properties properties)
        (conj (us/validate-user-properties-payload properties))
        flatten))))

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
  (let [user (update-in user [:email] clojure.string/lower-case)]
    (case origin
     :pav (check-pwd (:password user) (:password (dynamo-dao/get-user-by-email (:email user))))
     :facebook (user-exist? user))))

(defn authenticate-user [user origin]
  (let [user (update-in user [:email] clojure.string/lower-case)]
    {:record (update-user-token user origin)}))

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

(defn mark-notification [id]
  (dynamo-dao/mark-notification id))

(defn get-notifications [user from]
  (dynamo-dao/get-notifications user from))

(defn get-timeline [user from]
  (dynamo-dao/get-user-timeline user from))

(defn get-feed [user from]
  (dynamo-dao/get-user-feed user from))

(defn publish-following-event [follower following]
  (thread
   (let [following-event {:user_id follower :following_id following :timestamp (.getTime (Date.))}]
     (process-event (create-followinguser-timeline-event following-event)))))

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
  ([user_id private?]
   (if-let [u (get-user-by-id user_id)]
     (-> (profile-info u private?)
         (assoc :total_followers (count-followers user_id))
         (assoc :total_following (count-following user_id)))))
  ([current-user user_id private?]
     (if-let [u (get-user-profile user_id private?)]
       (assoc u :following
                (if current-user
                  (following? current-user user_id)
                  false)))))

(defn user-profile-exist?
  "Retrieve user profile, option to include current user for extra meta data on the relationship between both users.
  Response includes response suitable for Liberator use."
  ([user_id]
   (if-let [profile (get-user-profile user_id true)]
     [true {:record profile}]
     [false {:error {:error_message "User Profile does not exist"}}]))
  ([current-user user-viewing]
   (if-let [profile (get-user-profile current-user user-viewing false)]
     [true {:record profile}]
     [false {:error {:error_message "User Profile does not exist"}}])))

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

(defn valid-reset-token? [reset_token]
  (not (empty? (redis-dao/retrieve-useremail-by-reset-token reset_token))))

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

(defn mime-type->file-type [type]
  "Convert mime content-type to valid file type."
  (case type
    "image/jpeg" ".jpeg"
    "image/png" ".png"
    nil))

(defn valid-image? [file]
  "Issue file upload a valid image type, e.g. jpeg or png file"
  (if (or (empty? file) (nil? (mime-type->file-type (file :content-type))) (<= (file :size) 0))
    false
    true))

(defn upload-profile-image [user_id file]
  (let [user (get-user-by-id user_id)
        new-image-key (str "users/" user_id "/profile/img/p200xp200x/" (.toString (UUID/randomUUID)) (mime-type->file-type (file :content-type)))
        new-img_url {:img_url (str (:cdn-url env) (str "/" new-image-key))}]
    (when user
      (try
        (s3/upload-user-profile-image (:cdn-bucket-name env) new-image-key file)
        (update-account-settings user_id new-img_url)
        new-img_url
        (catch Exception e (log/error "Error uploading Profile image. " e))))))

(defn- merge-open-graph [body]
  "If an article_link is present, then merge open graph data or return original body"
  (if-let [url (:article_link body)]
    (merge body (gp/extract-open-graph url))
    body))

(defn- retrieve-bill-title [bill_id]
  "Given bill_id, Retrieve short_title if it exists or official_title."
  (when bill_id
    (when-let [bill-info (get-bill-info bill_id)]
     {:bill_title (or (:short_title bill-info) (:official_title bill-info))})))

(defn- construct-issue-feed-object
  "Given a user object and issue data, construct feed object for populating a users feed."
  [{:keys [user_id] :as user} {:keys [timestamp issue_id] :as issue-data}]
  (have map? user)
  (have map? issue-data)
  {:timestamp timestamp :type "userissue" :author_id user_id :issue_id issue_id})

(defn create-bill-issue
  "Create new bill issue, according to the details."
  [user_id details]
  (when-let [user (get-user-by-id user_id)]
    (let [details (merge {:user_id user_id} (merge-open-graph details) (retrieve-bill-title (:bill_id details)))
          new-issue (dynamo-dao/create-bill-issue details)
          to-populate (construct-issue-feed-object user new-issue)]
      ;; populate followers table as the last action
      (dynamo-dao/publish-as-global-feed-item to-populate)
      (dynamo-dao/publish-to-timeline user_id to-populate)
      (merge new-issue {:emotional_response "none"} (select-keys user [:first_name :last_name :img_url])))))

(declare get-user-issue-emotional-response)
(defn update-user-issue
  "Update user issue"
  [user_id issue_id update-map]
  (let [update-map (merge (merge-open-graph update-map) (retrieve-bill-title (:bill_id update-map)))]
    (println "Updated map" update-map)
    (merge
      (dynamo-dao/update-user-issue user_id issue_id update-map)
      (select-keys (get-user-by-id user_id) [:first_name :last_name :img_url])
      (get-user-issue-emotional-response issue_id user_id))))

(declare get-user-issue)
(defn delete-user-issue
  "Mark user issue as deleted.  Removes issue from users personal timeline and each user who has a copy on there newsfeed."
  [user_id issue_id]
  (when-let [{:keys [issue_id timestamp]} (or (get-user-issue user_id issue_id) (get-user-issue user_id (utils/base64->uuidStr issue_id)))]
    (dynamo-dao/mark-user-issue-for-deletion user_id issue_id)
    (dynamo-dao/delete-user-issue-from-timeline user_id timestamp)
    (dynamo-dao/delete-user-issue-from-feed issue_id)))

(defn validate-user-issue-emotional-response
  "Check if emotional_response parameter is in valid range. Returns inverted logic
so it can be fed to ':malformed?' handler."
  [body]
  (not
   (when-let [resp (:emotional_response body)]
     (and (utils/has-only-keys? body [:emotional_response])
          (some #{resp} ["positive" "neutral" "negative" "none"])))))

(defn validate-newissue-payload [payload]
  (us/validate-new-issue-payload payload))

(defn update-user-issue-emotional-response
  "Set emotional response for given issue_id."
  [issue_id user_id body]
  (when-let [resp (:emotional_response body)]
    (dynamo-dao/update-user-issue-emotional-response issue_id user_id resp)
    ;; return body as is, since we already check it's content with
    ;; 'validate-user-issue-emotional-response'
    body))

(defn get-user-issue-emotional-response
  "Retrieve emotional response for given issue_id."
  [issue_id user_id]
  (select-keys (dynamo-dao/get-user-issue-emotional-response issue_id user_id)
               [:emotional_response]))

(defn delete-user-issue-emotional-response
  "Delete emotional response for given issue_id and user_id"
  [issue_id user_id]
  (select-keys (dynamo-dao/delete-user-issue-emotional-response issue_id user_id)
    [:emotional_response]))

(defn user-issue-exist? [issue_id]
  (not (empty? (dynamo-dao/get-user-issue issue_id))))

(defn get-user-issue
  ([issue_id] (dynamo-dao/get-user-issue issue_id))
  ([user_id issue_id] (or
                        (dynamo-dao/get-user-issue user_id issue_id)
                        (dynamo-dao/get-user-issue user_id (utils/base64->uuidStr issue_id)))))

(defn get-user-issue-feed-item
  "Retrieve Single User Issue in the same format as that displayed in a feed item."
  ([issue_id & [user_id]]
   (when-let [issue (or (get-user-issue issue_id)
                        ;; To accomdate social sharing we might need to retrieve an issue id by short_issue_id field
                        (get-user-issue (utils/base64->uuidStr issue_id)))]
     (merge
       (if (empty? (:short_issue_id issue))
         (assoc issue :short_issue_id (utils/uuid->base64Str (UUID/fromString issue_id)))
         issue)
       (select-keys (get-user-by-id (:user_id issue)) [:first_name :last_name :img_url])
       (if user_id
         (select-keys (dynamo-dao/get-user-issue-emotional-response issue_id user_id) [:emotional_response])
         {:emotional_response "none"})))))

(defn contact-form-email-malformed? [body]
  (us/validate-contact-form body))

(defn send-contact-form-email [body]
  (mandril/send-contact-form-email body))
