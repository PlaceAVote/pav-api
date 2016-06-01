(ns com.pav.api.db.followers
  "User followers and following management."
  (:require [com.pav.api.db.db :as db]
            [com.pav.api.db.tables :as t]
            [com.pav.api.db.topic :refer [add-user-topics]]
            [com.pav.api.db.common :refer [unclobify extract-value]]
            [clojure.java.jdbc :as sql]
            [clojure.tools.logging :as log]
            [com.pav.api.utils.utils :refer [sstr current-time]]))

(declare following?)

(defn follow-user
  "Create new following relation. If 'timestamp' is not set, it will
user current time."
  ([follower_id following_id timestamp]
     (assert (not= follower_id following_id) "You are trying to follow itself.")
     (when-not (following? follower_id following_id)
       (sql/with-db-transaction [d db/db]
         (sql/insert! d t/user-followers-table {:user_id follower_id
                                                :following_id following_id
                                                :created_at timestamp}))))
  ([follower_id following_id] (follow-user follower_id following_id (current-time))))

(defn unfollow-user
  "Remove following relation."
  [follower_id following_id]
  (sql/delete! db/db t/user-followers-table ["user_id = ? AND following_id = ?" follower_id following_id]))

(defn user-following
  "Return all id's this user follows."
  [user_id]
  (some->> (sql/query db/db [(sstr "SELECT following_id FROM " t/user-followers-table " WHERE user_id = ?") user_id])
           seq
           (map :following_id)))

(defn user-followers
  "Return all id's that follows this user."
  [user_id]
  (some->> (sql/query db/db [(sstr "SELECT user_id FROM " t/user-followers-table " WHERE following_id = ?") user_id])
           seq
           (map :user_id)))

(defn count-followers
  "Number of followers."
  [user_id]
  (extract-value (sql/query db/db [(sstr "SELECT COUNT(following_id) FROM " t/user-followers-table " WHERE user_id = ?") user_id])))

(defn count-following
  "How many users this user follows."
  [user_id]
  (extract-value (sql/query db/db [(sstr "SELECT COUNT(user_id) FROM " t/user-followers-table " WHERE following_id = ?") user_id])))

(defn following?
  "Check if 'follower' follows 'following'."
  [follower following]
  (-> (sql/query db/db [(sstr "SELECT * FROM " t/user-followers-table " WHERE user_id = ? AND following_id = ? LIMIT 1") follower following])
      seq
      boolean))
