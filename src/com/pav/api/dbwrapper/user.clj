(ns com.pav.api.dbwrapper.user
  "Temporary wrapper that does dynamodb and sql parallel storage."
  (:require [com.pav.api.dynamodb.user :as dynamo]
            [com.pav.api.db.user :as sql]
            [com.pav.api.utils.utils :refer [prog1]])
  (:import java.text.SimpleDateFormat
           java.util.Date))

(defn- parse-dob
  "Parse date of birth in form MM/DD/YYYY to long."
  [in]
  (-> (SimpleDateFormat. "MM/dd/yyyy")
      (.parse in)
      .getTime))

;  {:user_id 7f55d570-49fc-475e-8c2a-35ad747099f4,
;   :email test651@placeavote.com,
;   :password bcrypt+sha512$7be7c879944b55628c8c1d30$12$24326124313224566a6757384e62397a757673384c346964346971534f5868364d626d2f7079567964436853675248367a517331436b775873554f61,
;   :first_name john,
;   :last_name stuff,
;   :dob 05/10/1984,
;   :country_code USA,
;   :state CA,
;   :address Beverly Hills, CA 90210, USA,
;   :lat 34.1030032,
;   :lng -118.4104684,
;   :district 33,
;   :created_at 1461873506572,
;   :public true,
;   :registered nil, :token "xx"
;   :topics [Defense],
;   :confirmation-token 2157be72-0afd-416c-839e-0dbc0a71ccfc,
;   :zipcode 90210,
;   :gender male}

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
   (-> user-profile convert-user-profile sql/create-user)))

(defn delete-user [user-id]
  (prog1
   (dynamo/delete-user user-id)
   (sql/delete-user user-id)))
