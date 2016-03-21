(ns com.pav.user.api.database.user
  (:require [com.pav.user.api.database.database :refer [db-spec]]
            [clojure.java.jdbc :as j]
            [clj-time.jdbc]
            [clj-time.format :as f]
            [clj-time.coerce :as c]
            [clojure.tools.logging :as log]))

(def dob-formatter (f/formatter "MM/dd/yyyy"))

(defn create-user [user]
  (let [to-persist (-> (select-keys user [:user_id :email :first_name :last_name :gender :dob :country_code
                                          :state :district :created_at :topics :img_url :public :origin :token])
                       (update-in [:topics] #(clojure.string/join "," %))
                       (update-in [:dob] #(f/parse dob-formatter %))
                       (update-in [:created_at] c/from-long))]
    (log/info "Persisting user to MYSQL " to-persist)
    (j/insert! db-spec :user_info to-persist)))
