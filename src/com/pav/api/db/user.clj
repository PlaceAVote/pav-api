(ns com.pav.api.db.user
  "Functions for dealing with user database access."
  (:require [com.pav.api.db.db :as db]
            [com.pav.api.db.tables :as t]
            [com.pav.api.db.topic :refer [add-user-topics]]
            [com.pav.api.db.common :refer [unclobify extract-value]]
            [clojure.java.jdbc :as sql]
            [clojure.tools.logging :as log]
            [com.pav.api.utils.utils :refer [sstr current-time]]))

(defn get-user-by-id
  "Return user by given id. id can be either number or number as string.
Returns nil if not found."
  [id]
  (first
   (sql/query db/db [(sstr "SELECT * FROM " t/user-info-table " WHERE user_id = ? LIMIT 1") id])))

(defn get-user-by-old-id
  "Return user by old ID schema used for DynamoDB. User ID in this case is
UUID value, instead autogenerated number by database. Do not use this function
other for easier transition from DynamoDB, since it will be removed in future."
  [id]
  (first
   (sql/query db/db [(sstr "SELECT * FROM " t/user-info-table " WHERE old_user_id = ? LIMIT 1") id]
              ;; Remove Clob from every value if found. This is primarly for H2
              ;; database, since it will for TEXT types return Java Clob object. MySQL will
              ;; however return normal String. Note that Clob isn't compatible with String.
              {:row-fn unclobify})))

(defn get-user-by-email
  "Return user by given email address."
  [email]
  (first
   (sql/query db/db [(sstr "SELECT * FROM " t/user-info-table " WHERE email = ? LIMIT 1") email]
              {:row-fn unclobify})))

(defn get-user-profile-by-facebook-id
  "Return full user details by given facebook id."
  [facebook_id]
  (first
   (sql/query db/db [(sstr "SELECT * FROM " t/user-info-table " AS u "
                           "JOIN " t/user-creds-fb-table " AS c "
                           "ON u.user_id = c.user_id "
                           "WHERE c.facebook_id = ?") facebook_id])))

(defn- create-confirmation-record
  "Helper to insert confirmation token. Be careful with calling
since function is not executed in transaction intentionally, so it can be
safely called in 'create-user' transaction."
  [trans-db user_id token]
  (sql/insert! trans-db t/user-confirmation-tokens-table {:user_id user_id
                                                          :token token
                                                          :created_at (current-time)}))

(defn- store-user-password
  "Use given email, id and password hash and store it in user_creds_pav table. Do
that in a given transaction."
  [trans-db email id password]
  (sql/insert! trans-db t/user-creds-pav-table {:email email
                                                :user_id id
                                                :password password}))
(defn- store-fb-id-token
  "Assign facebook ID and token for given user."
  [trans-db id fb-id fb-token]
  (sql/insert! trans-db t/user-creds-fb-table {:facebook_id fb-id
                                               :facebook_token fb-token
                                               :user_id id}))

(defn- figure-id
  "Depending on parameter, returns new/old style ID."
  [id is-old-id?]
  (if is-old-id?
    (-> id get-user-by-old-id :user_id)
    id))

(defn- get-user-password
  "Retrieve user password using id or old_id, depending on occasion."
  ([id is-old-id?]
     (let [id (figure-id id is-old-id?)]
       (-> (sql/query db/db
                      [(sstr "SELECT password FROM " t/user-creds-pav-table " WHERE user_id = ?") id]
                      {:row-fn unclobify})
           first
           :password)))
  ([id] (get-user-password id false)))

(defn- get-fb-id-and-token
  "Retrieve facebook token and id using database id or old_id."
  ([id is-old-id?]
     (let [id (figure-id id is-old-id?)]
       (first
        (sql/query db/db [(sstr "SELECT facebook_id, facebook_token FROM " t/user-creds-fb-table " WHERE user_id = ? LIMIT 1") id]))))
  ([id] (get-fb-id-and-token id false)))

(defn create-user
  "Create user with given map. Can throw constraint exception from database if email is
not unique. Returns user id if everything went fine.

If :password was given, it will create user with local credentials. If not, it will except
facebook :token value, which will create facebook related credentials (inside user_creds_fb)."
  [user-profile]
  {:pre [(and (contains? user-profile :email)
              (contains? user-profile :confirmation-token)
              (or (contains? user-profile :password)
                  (contains? user-profile :facebook_token)))]}
  (try
    (sql/with-db-transaction [d db/db]
      ;; Select only those keys that matches table columns, or database will throw exception.
      ;; The reason for this is because 'user-profile' contains (and can contain) more details
      ;; shared beteween multiple tables, necessary for user storage, like password or confirmation-token.
      (let [data (select-keys user-profile [:email :first_name :last_name :img_url :gender
                                            :dob :address :zipcode :state :latitude :longtitude
                                            :public_profile :created_at :updated_at :country_code
                                            :old_user_id])
            data (unclobify data)
            pass (:password user-profile)
            id (extract-value
                (sql/insert! d t/user-info-table data))]
        ;; confirmation token
        (->> user-profile :confirmation-token (create-confirmation-record d id))
        ;; password or fb details
        (if pass
          (store-user-password d (:email user-profile) id pass)
          (store-fb-id-token d id (:facebook_id user-profile) (:facebook_token user-profile)))
        ;; topics
        (->> user-profile :topics (add-user-topics d id))
        id))
    (catch Exception e
      (log/error e "Error occured persisting new user-profile to SQL table with details:" user-profile))))

(defn delete-user
  "Delete user, making sure all tables are emptied wher user_id is referenced."
  [user_id]
  (sql/delete! db/db t/user-info-table ["user_id = ?" user_id]))

(defn update-user-token
  "Update user token."
  [user_id token]
  (sql/update! db/db t/user-confirmation-tokens-table {:token token} ["user_id = ?" user_id]))

(defn update-facebook-user-token [user_id new-facebook-token new-token]
  )

(defn update-registration [token]
  )

(defn update-user-password [user_id password]
  (sql/update! db/db t/user-creds-pav-table {:password password} ["user_id = ?" user_id]))

(defn update-account-settings [user_id param-map]
  )

(defn assign-facebook-id [user_id facebook_id]
  )

(defn retrieve-all-user-records
  "Performs full table scan and retrieves all user records"
  []
  (sql/query db/db (sstr "SELECT * FROM " t/user-info-table)))

(defn user-count-between
  "Return number of users created between 'start' and 'end' dates."
  [start end]
  (extract-value
   (sql/query db/db [(sstr "SELECT COUNT(*) FROM " t/user-info-table " WHERE created_at >= ? AND created_at <=?") start end])))