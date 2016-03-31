(ns com.pav.user.api.migrations.migrations
  (:require [com.pav.user.api.dynamodb.db :as db]
            [com.pav.user.api.dynamodb.user :as dynamo-dao]
            [com.pav.user.api.services.users :as user-service]
            [taoensso.faraday :as far]
            [clojure.tools.logging :as log])
  (:import (java.util UUID)))

(defn- pre-populate-newsfeed
  "Pre-populate user feed with bills related to chosen subjects and last two issues for each default follower."
  [{:keys [user_id topics]}]
  (let [cached-bills (map user-service/add-timestamp (user-service/gather-cached-bills topics))
        feed-items   (mapv #(assoc % :event_id (.toString (UUID/randomUUID)) :user_id user_id) cached-bills)]
    (if (seq feed-items)
      (dynamo-dao/persist-to-newsfeed feed-items))))

(defn retrieve-all-user-records
  "This is a temporary function, only meant to be run once."
  []
  (log/info "Retrieving all user records by scanning user table")
  (loop [user-records (far/scan db/client-opts db/user-table-name)
         acc []]
    (if (:last-prim-kvs (meta user-records))
      (recur (far/scan db/client-opts db/user-table-name {:last-prim-kvs (:last-prim-kvs (meta user-records))})
             (into acc user-records))
      acc)))

(defn add-feed-content
  "Scan user records and add new bill content that matches there topic selections"
  []
  (log/info "Starting to populate existing user feeds with new content")
  (map pre-populate-newsfeed (retrieve-all-user-records))
  (log/info "Finished populating existing user feeds with new content"))
