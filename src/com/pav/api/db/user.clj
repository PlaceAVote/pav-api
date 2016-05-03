(ns com.pav.api.db.user
  "Functions for dealing with user database access."
  (:require [com.pav.api.db.db :as db]
            [clojure.java.jdbc :as sql]
            [clojure.tools.logging :as log]
            [com.pav.api.utils.utils :as u]))

(defn- extract-value
  "Extract value returned from query result, where we are interested only
in value, but now query key. This works for counting clauses, finding id of
only one id or extracting generated id. Makes no sense for multiple results."
  [ret]
  (-> ret first first val))

(defn get-user-by-id
  "Return user by given id. id can be either number or number as string.
Returns nil if not found."
  [id]
  (first 
   (sql/query db/db ["SELECT * FROM user_info WHERE user_id = ? LIMIT 1" id])))

(defn get-user-by-email
  "Return user by given email address."
  [email]
  (first 
   (sql/query db/db ["SELECT * FROM user_info WHERE email = ? LIMIT 1" email])))

(defn get-user-profile-by-facebook-id
  "Return full user details by given facebook id."
  [facebook_id]
  (first 
   (sql/query db/db [(str "SELECT * FROM user_info AS u "
                          "JOIN user_creds_fb AS c "
                          "ON u.user_id = c.user_id "
                          "WHERE c.facebook_id = ?") facebook_id])))

(defn- create-confirmation-record
  "Helper to insert confirmation token. Be careful with calling
since function is not executed in transaction intentionally, so it can be
safely called in 'create-user' transaction."
  [trans-db user_id token]
  (sql/insert! trans-db "user_confirmation_tokens" {:user_id user_id
                                                    :token token
                                                    :created_at (u/current-time)}))

(defn- store-user-password
  "Use given email, id and password hash and store it in user_creds_pav table. Do
that in a given transaction."
  [trans-db email id password]
  (sql/insert! trans-db "user_creds_pav" {:email email
                                          :user_id id
                                          :password password}))

(defn create-user
  "Create user with given map. Can throw constraint exception from database if email is
not unique. Returns user id if everything went fine."
  [user-profile]
  {:pre [(and (contains? user-profile :email)
              (contains? user-profile :password)
              (contains? user-profile :confirmation-token))]}
  (try
    (sql/with-db-transaction [d db/db]
      (let [data (dissoc user-profile :confirmation-token)
            id (extract-value
                (sql/insert! d "user_info" data))]
        (->> user-profile
             :confirmation-token
             (create-confirmation-record d id))
        (store-user-password d (:email user-profile) id (:password user-profile))
        id))
    (catch Exception e
      (log/error e "Error occured persisting new user-profile to SQL table with details:" user-profile))))

(defn delete-user [user_id]
  (sql/delete! db/db "user_info" ["user_id = ?" user_id]))

(defn update-user-token [user_id new-token]
  )

(defn update-facebook-user-token [user_id new-facebook-token new-token]
  )

(defn update-registration [token]
  )

(defn update-user-password [user_id password]
  )

(defn update-account-settings [user_id param-map]
  )

(defn assign-facebook-id [user_id facebook_id]
  )

(defn retrieve-all-user-records
  "Performs full table scan and retrieves all user records"
  []
  (sql/query db/db "SELECT * FROM user_info"))

(defn user-count-between
  "Return number of users created between 'start' and 'end' dates."
  [start end]
  (extract-value
   (sql/query db/db
              ["SELECT COUNT(*) FROM user_info WHERE created_at >= ? AND created_at <=?" start end])))
