(ns com.pav.user.api.migrations.migrations
  (:gen-class)
  (:require [com.pav.user.api.dynamodb.db :as db]
            [com.pav.user.api.services.users :as user-service]
            [taoensso.faraday :as far]
            [clojure.tools.logging :as log])
  (:import (java.util UUID)))

(defn- persist-to-newsfeed [events]
  (when events
    (try
      (far/batch-write-item db/client-opts {db/userfeed-table-name {:put events}})
      (catch Exception e (log/error (str "Problem occurred persisting event to new users feed: " events) e)))))

(defn- pre-populate-newsfeed
  "Pre-populate user feed with bills related to chosen subjects and last two issues for each default follower."
  [{:keys [user_id topics]}]
  (log/info "Populating feed for " user_id " with bills for topics " topics)
  (let [cached-bills (map user-service/add-timestamp (user-service/gather-cached-bills topics))
        feed-items   (mapv #(assoc % :event_id (.toString (UUID/randomUUID)) :user_id user_id) cached-bills)]
    (log/info (str "Populating feed for " user_id " with " (count feed-items) " items."))
    (if (seq feed-items)
      (persist-to-newsfeed feed-items))))

(defn retrieve-all-user-records
  "This is a temporary function, only meant to be run once."
  []
  (log/info "Retrieving all user records by scanning user table")
  (loop [user-records (far/scan db/client-opts db/user-table-name)
         acc []]
    (log/info (str "Retrieved " (count user-records) " user records from " db/user-table-name))
    (if (:last-prim-kvs (meta user-records))
      (recur (far/scan db/client-opts db/user-table-name {:last-prim-kvs (:last-prim-kvs (meta user-records))})
        (into acc user-records))
      (into acc user-records))))

(defn -main
  "Scan user records and add new bill content that matches there topic selections"
  []
  (log/info "Starting to populate existing user feeds with new content")
  (doseq [user-record (retrieve-all-user-records)]
    (pre-populate-newsfeed user-record))
  (log/info "Finished populating existing user feeds with new content"))
