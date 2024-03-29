(ns com.pav.api.dbwrapper.user
  "Temporary wrapper that does dynamodb and sql parallel storage."
  (:require [com.pav.api.dynamodb.user :as dynamo]
            [com.pav.api.db.user :as sql]
            [com.pav.api.dbwrapper.helpers :refer [with-sql-backend bigint->long]]
            [com.pav.api.utils.utils :refer [prog1]]
            [com.pav.api.redis.redis :as redis])
  (:import java.text.SimpleDateFormat))

(defn parse-dob
  "Parse date of birth in form MM/DD/YYYY to long."
  [in]
  (cond
   (string? in) (-> (SimpleDateFormat. "MM/dd/yyyy")
                    (.parse in)
                    .getTime)
   ;; some field from dynamo has this type
   (instance? clojure.lang.BigInt in) (bigint->long in)
   (number? in) in
   :else (throw
          (Exception.
           (format "Received wrong format (%s) for '%s'" (type in) in)))))

(defn convert-user-profile
  "Convert it from dynamodb schema to sql db schema."
  [user-profile]
  (assoc user-profile
    :old_user_id (:user_id user-profile)
    :latitude (:lat user-profile)
    :longtitude (:lng user-profile)
    :district (-> user-profile :district bigint->long)
    :public_profile (:public user-profile)
    :created_at (-> user-profile :created_at bigint->long)
    :updated_at (-> user-profile :updated_at bigint->long)
    :dob (-> user-profile :dob bigint->long)))

(defn format-account-settings [updates]
  (cond-> (select-keys updates [:email :first_name :last_name
                                :gender :zipcode :state :address
                                :dob :district :img_url])
    (:lat updates)      (assoc :latitude (:lat updates))
    (:lng updates)      (assoc :longtitude (:lng updates))
    (:district updates) (update-in [:district] bigint->long)
    (:dob updates)      (update-in [:dob] bigint->long)
    (:public updates)   (assoc :public_profile (:public updates))))

(defn create-user [user-profile]
  (prog1
   (dynamo/create-user user-profile)
   (with-sql-backend
     (-> user-profile convert-user-profile sql/create-user))))

(defn delete-user [user-id]
  (prog1
   (dynamo/delete-user user-id)
   (with-sql-backend
     (sql/delete-user user-id))))

(defn update-user-profile [user_id updates]
  (prog1
    (dynamo/update-account-settings user_id updates)
    (redis/update-account-settings user_id updates)
    (with-sql-backend
      (->>
        updates
        format-account-settings
        (sql/update-account-settings (:user_id (sql/get-user-by-old-id user_id)))))))
