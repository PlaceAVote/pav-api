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
  (let [to-persist (-> (select-keys user [:user_id :email :first_name :last_name :gender :dob :facebook_id
                                          :state :district :country_code :zipcode :lat :lng :address
                                          :created_at :topics :img_url :public :origin :token])
                       (update-in [:topics] #(clojure.string/join "," %))
                       (update-in [:dob] #(f/parse dob-formatter %))
                       (update-in [:created_at] c/from-long))]
    (log/info "Persisting user to MYSQL " to-persist)
    (j/insert! db-spec :user_info to-persist)))

(defn get-user [query]
  (-> (j/query db-spec query) ud/convert-to-correct-profile-type))

(defn get-user-by-id [user_id]
  (get-user ["SELECT * FROM user_info WHERE user_id = ?" user_id]))

(defn get-user-by-email [email]
  (get-user ["SELECT * FROM user_info WHERE email = ?" email]))

(defn get-user-by-facebook [facebook_id]
  (get-user ["SELECT * FROM user_info WHERE facebook_id = ?" facebook_id]))
