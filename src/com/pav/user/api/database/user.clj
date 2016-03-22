(ns com.pav.user.api.database.user
  (:require [com.pav.user.api.database.database :refer [db-spec]]
            [com.pav.user.api.domain.user :as ud]
            [clojure.java.jdbc :as j]
            [clj-time.jdbc]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-time.coerce :as c]
            [clojure.tools.logging :as log]))

(def ^{:doc "DOB Formatter"}
dob-formatter (f/formatter (t/default-time-zone) "MM/dd/yyyy" "MM/dd/yyyy"))

(defn dob->date [val]
  (when val
    (f/parse dob-formatter val)))

(defn sql-date->str [val]
  (f/unparse dob-formatter (c/from-sql-date val)))

(defn date->long [val]
  (c/to-long val))

(defn create-user [user]
  (let [to-persist (-> (select-keys user [:user_id :email :password :facebook_id
                                          :first_name :last_name :gender :dob
                                          :state :district :country_code :zipcode :lat :lng :address
                                          :created_at :topics :img_url :public :origin :token])
                       (update-in [:topics] #(clojure.string/join "," %))
                       (update-in [:dob] dob->date)
                       (update-in [:created_at] c/from-long))]
    (log/info "Persisting user: " to-persist)
    (j/insert! db-spec :user_info to-persist)))

(defn format-user-result-set
  "Take each user row of a result set and transform for presentation."
  [user]
  (-> (update-in user [:dob] sql-date->str)
      (update-in [:created_at] date->long)))

(defn get-user [query]
  (-> (j/query db-spec query :row-fn format-user-result-set)
      first ud/convert-to-correct-profile-type))

(defn get-user-by-id [user_id]
  (get-user ["SELECT * FROM user_info WHERE user_id = ?" user_id]))

(defn get-user-by-email [email]
  (get-user ["SELECT * FROM user_info WHERE email = ?" email]))

(defn get-user-by-facebook [facebook_id]
  (get-user ["SELECT * FROM user_info WHERE facebook_id = ?" facebook_id]))

(defn update-user-token [user_id new-token]
  (log/info "Updating user token for " user_id)
  (j/update! db-spec :user_info {:token new-token} ["user_id = ?" user_id]))

(defn update-password [user_id new-pwd]
  (log/info "Updating password for " user_id)
  (j/update! db-spec :user_info {:password new-pwd} ["user_id = ?" user_id]))

(defn prepare-account-settings
  "Take collection of account settings and prepare them for persistance"
  [record]
  (into record
    (for [[k v] (select-keys record [:email :first_name :last_name :dob :public :gender])]
     (cond
       (= k :dob) [k (dob->date v)]
       :else [k v]))))

(defn follow-user [follower_id following_id]
  (log/info "Creating follower relationship between " follower_id " and " following_id)
  (j/insert! db-spec :user_following_rel {:follower_id follower_id :following_id following_id}))

(defn following?
  "Is there a following relationship between both users"
  [follower_id following_id]
  (if (-> (j/query db-spec ["SELECT * FROM user_following_rel WHERE follower_id = ? AND following_id = ?" follower_id following_id]) first)
    true
    false))

(defn count-followers [user_id]
  (-> (j/query db-spec ["SELECT count(*) AS count FROM user_following_rel WHERE following_id = ?" user_id]) first :count))

(defn count-following [user_id]
  (-> (j/query db-spec ["SELECT count(*) AS count FROM user_following_rel WHERE follower_id = ?" user_id]) first :count))

(defn update-userprofile [user_id param-map]
  (log/info "Updating user profile for " user_id)
  (j/update! db-spec :user_info
    (prepare-account-settings param-map) ["user_id = ?" user_id]))
