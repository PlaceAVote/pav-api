(ns com.pav.user.api.domain.user
  (:require [environ.core :refer [env]]
            [buddy.hashers :as h]
            [com.pav.user.api.authentication.authentication :refer [create-auth-token]])
  (:import (java.util UUID)
           (java.util Date)))

(defn assign-new-token [user]
  (let [safe-profile (dissoc user :token)
        new-token (create-auth-token safe-profile)]
    (merge safe-profile new-token)))

(defn assoc-common-attributes [user-profile]
  (-> user-profile
      (assoc :user_id (.toString (UUID/randomUUID))
             :created_at (.getTime (Date.))
             :confirmation-token (.toString (UUID/randomUUID)))
      (merge {:registered false :public true})))

(defn hash-password [user-profile]
  (update-in user-profile [:password] h/encrypt))

(defprotocol Profiles
  (assigntoken [profile]
    "Assign new token to user profile")
  (presentable [profile]
    "Remove sensitive information from user profiles"))

(defrecord UserProfile [user_id email password first_name last_name dob country_code
                        created_at public registered token topics confirmation-token]
  Profiles
  (presentable [profile]
    (dissoc profile :password :confirmation-token))
  (assigntoken [profile]
    (-> (create-auth-token (dissoc profile :password :token))
        (merge profile))))

(defrecord FacebookUserProfile [user_id email facebook_token first_name last_name dob country_code
                                created_at public registered token topics confirmation-token]
  Profiles
  (presentable [profile]
    (dissoc profile :facebook_token :confirmation-token))
  (assigntoken [profile]
    (-> (create-auth-token (dissoc profile :token))
        (merge profile {:facebook_token (:token profile)}))))

(defn new-user-profile [user-profile origin]
  (case origin
    :pav 			(map->UserProfile (->
																	user-profile
															 		assoc-common-attributes
																	hash-password
																	assign-new-token))
    :facebook (map->FacebookUserProfile (->
																					user-profile
																					assoc-common-attributes
																					(merge {:facebook_token (:token user-profile)})
																					assign-new-token))
    nil))

(defn convert-to-correct-profile-type [user-profile]
  (when user-profile
    (if (contains? user-profile :facebook_token)
     (map->FacebookUserProfile user-profile)
     (map->UserProfile user-profile))))

