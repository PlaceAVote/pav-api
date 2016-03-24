(ns com.pav.user.api.database.issues
  (:require [com.pav.user.api.database.database :refer [db-spec]]
            [com.pav.user.api.utils.utils :refer [uuid->base64Str]]
            [clojure.java.jdbc :as j]
            [clj-time.jdbc]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-time.coerce :as c]
            [clojure.tools.logging :as log])
  (:import (java.util Date UUID)))

(defn create-user-issue [issue]
  (let [id (UUID/randomUUID)
        short_id (uuid->base64Str id)
        to-persist (merge
                     (update-in issue [:timestamp] c/from-long)
                     {:issue_id (.toString id) :short_issue_id short_id :timestamp (c/to-sql-time (t/now))})]
    (log/info "Persisting user issue " to-persist)
    (j/insert! db-spec :user_issues to-persist)
    to-persist))
