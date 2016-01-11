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
      (merge {:public true})))

(defn hash-password [user-profile]
  (update-in user-profile [:password] h/encrypt))

(defn- extract-profile-info [profile]
	(select-keys profile [:user_id :first_name :last_name :country_code :public :img_url]))

(defprotocol Profiles
  (presentable [profile]
    "Remove sensitive information from user profiles")
	(profile-info [profile]
		"Return user profile information")
	(create-token-for [profile]
		"Assign a new token to the profile"))

(defrecord UserProfile [user_id email password first_name last_name dob country_code
                        created_at public registered token topics confirmation-token]
  Profiles
  (presentable [profile]
    (dissoc profile :password :confirmation-token))
	(profile-info [profile]
		(extract-profile-info profile))
	(create-token-for [profile]
		(assign-new-token (dissoc profile :password :token))))

(defrecord FacebookUserProfile [user_id email facebook_token facebook_id first_name last_name dob country_code
                                created_at public registered token topics confirmation-token]
  Profiles
  (presentable [profile]
    (dissoc profile :facebook_token :confirmation-token))
	(profile-info [profile]
		(extract-profile-info profile))
	(create-token-for [profile]
		(assign-new-token (dissoc profile :token))))

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
																					(merge {:facebook_token (:token user-profile) :facebook_id (:id user-profile)})
																					assign-new-token))
    nil))

(defn convert-to-correct-profile-type [user-profile]
  (when (seq user-profile)
    (if (contains? user-profile :facebook_token)
     (map->FacebookUserProfile user-profile)
     (map->UserProfile user-profile))))

