(ns com.pav.user.api.database.user
  (:require [com.pav.user.api.database.database :refer [db-spec]]
            [com.pav.user.api.domain.user :as ud]
            [clojure.java.jdbc :as j]
            [clj-time.jdbc]
            [clj-time.format :as f]
            [clj-time.coerce :as c]
            [clojure.tools.logging :as log]))

(def dob-formatter (f/formatter "MM/dd/yyyy"))

(defn create-user [user]
  (let [to-persist (-> (select-keys user [:user_id :email :password :facebook_id
                                          :first_name :last_name :gender :dob
                                          :state :district :country_code :zipcode :lat :lng :address
                                          :created_at :topics :img_url :public :origin :token])
                       (update-in [:topics] #(clojure.string/join "," %))
                       (update-in [:dob] #(f/parse dob-formatter %))
                       (update-in [:created_at] c/from-long))]
    (log/info "Persisting user: " to-persist)
    (j/insert! db-spec :user_info to-persist)))

(defn get-user [query]
  (-> (j/query db-spec query) first ud/convert-to-correct-profile-type))

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
