(ns com.pav.api.db.vote
  (:require [com.pav.api.db.db :as db]
            [com.pav.api.db.tables :refer [user-votes-table]]
            [clojure.java.jdbc :as sql]
            [clojure.tools.logging :as log]
            [com.pav.api.utils.utils :as u]
            [com.pav.api.db.common :refer [extract-value unclobify]]))


(defn create-user-vote-record [vote]
  (sql/with-db-transaction [d db/db]
    (let [id (extract-value (sql/insert! d user-votes-table vote))]
      (log/info "Persisted User Vote to user_votes table" vote)
      id)))

(defn get-user-vote-by-old-id [vote_id]
  (first (sql/query db/db [(u/sstr "SELECT * FROM " user-votes-table " WHERE old_vote_id=?") vote_id])))
