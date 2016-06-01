(ns com.pav.api.domain.user
  (:require [environ.core :refer [env]]
            [buddy.hashers :as h]
            [com.pav.api.authentication.authentication :refer [create-auth-token]]
						[com.pav.api.location.location-service :refer [retrieve-location-by-zip]]
            [com.pav.api.facebook.facebook-service :refer [generate-long-lived-token]])
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
		  (update-in [:email] clojure.string/lower-case)
      (merge {:public true} (retrieve-location-by-zip (:zipcode user-profile)))))

(defn hash-password [user-profile]
  (update-in user-profile [:password] h/encrypt))

(defn- extract-profile-info [profile private?]
  (cond-> (select-keys profile [:user_id :first_name :last_name :country_code :state :public :img_url :city :registered])
    private? (merge (select-keys profile [:zipcode :lat :lng :email :district :gender :created_at]))))

(defprotocol Profiles
  (presentable [profile]
    "Remove sensitive information from user profiles")
	(profile-info [profile private?]
		"Return user profile information, if private option is provided then include additional sensitive information.")
	(assign-token-for [profile]
		"Assign a new token to the profile")
	(account-settings [profile]
		"Retrieve account settings for user")
	(indexable-profile [profile]
		"Prepare User profile for indexing to ES"))

(defrecord UserProfile [user_id email password first_name last_name dob country_code state address lat lng district
                        created_at public registered token topics confirmation-token]
  Profiles
  (presentable [profile]
    (dissoc profile :password :confirmation-token))
	(profile-info [profile private?]
    (extract-profile-info profile private?))
	(assign-token-for [profile]
		(assign-new-token (dissoc profile :password :token)))
	(account-settings [profile]
		(-> (select-keys profile [:user_id :first_name :last_name :dob :gender :public :email :img_url :city :zipcode])
			  (assoc :social_login false)))
	(indexable-profile [profile]
		(dissoc profile :password :token)))

(defrecord FacebookUserProfile [user_id email facebook_token facebook_id first_name last_name dob country_code
                                state address lat lng district created_at public registered token topics
                                confirmation-token]
  Profiles
  (presentable [profile]
    (dissoc profile :facebook_token :confirmation-token))
	(profile-info [profile private?]
    (extract-profile-info profile private?))
	(assign-token-for [profile]
		(assign-new-token (dissoc profile :token)))
	(account-settings [profile]
		(-> (select-keys profile [:user_id :first_name :last_name :dob :gender :public :email :img_url :city :zipcode])
			  (assoc :social_login true)))
	(indexable-profile [profile]
		(dissoc profile :token :facebook_token :facebook_id)))

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
																					(merge {:facebook_token (generate-long-lived-token (:token user-profile))
                                                  :facebook_id (:id user-profile)})
																					assign-new-token))
    nil))

(defn convert-to-correct-profile-type [user-profile]
  (when (seq user-profile)
    (if (contains? user-profile :facebook_token)
     (map->FacebookUserProfile user-profile)
     (map->UserProfile user-profile))))

