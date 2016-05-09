(ns com.pav.api.dbwrapper.user
  "Temporary wrapper that does dynamodb and sql parallel storage."
  (:require [com.pav.api.dynamodb.user :as dynamo]
            [com.pav.api.db.user :as sql]
            [com.pav.api.dbwrapper.helpers :refer [with-sql-backend]]
            [com.pav.api.utils.utils :refer [prog1]])
  (:import java.text.SimpleDateFormat
           java.util.Date))

(defn- parse-dob
  "Parse date of birth in form MM/DD/YYYY to long."
  [in]
  (-> (SimpleDateFormat. "MM/dd/yyyy")
      (.parse in)
      .getTime))

(defn- convert-user-profile
  "Convert it from dynamodb schema to sql db schema."
  [user-profile]
  (assoc user-profile
    :old_user_id (:user_id user-profile)
    :latitude (:lat user-profile)
    :longtitude (:lng user-profile)
    :public_profile (:public user-profile)
    :dob (-> user-profile :dob parse-dob)))

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
