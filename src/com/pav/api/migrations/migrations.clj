(ns com.pav.api.migrations.migrations
  (:gen-class)
  (:require [com.pav.api.dynamodb.db :as db]
            [com.pav.api.dynamodb.user :as dynamo-dao]
            [com.pav.api.services.users :as user-service]
            [taoensso.faraday :as far]
            [clojure.tools.logging :as log])
  (:import (java.util UUID)))

(defn- persist-to-newsfeed [events]
  (when events
      (doseq [event events]
        (try
          (log/info "Writing Events to Feed " (select-keys event [:event_id :user_id :timestamp :bill_id]))
          (far/put-item db/client-opts db/userfeed-table-name event)
        (catch Exception e (log/error (str "Problem occurred persisting event to new users feed: " event) e))))))

(defn- pre-populate-newsfeed
  "Pre-populate user feed with bills related to chosen subjects and last two issues for each default follower."
  [{:keys [user_id topics]}]
  (log/info "Populating feed for " user_id " with bills for topics " topics)
  (let [cached-bills (map user-service/add-timestamp (user-service/gather-cached-bills topics))
        feed-items   (mapv #(assoc % :event_id (.toString (UUID/randomUUID)) :user_id user_id) cached-bills)]
    (log/info (str "Populating feed for " user_id " with " (count feed-items) " items."))
    (if (seq feed-items)
      (persist-to-newsfeed feed-items))))

(defn -main
  "Scan user records and add new bill content that matches there topic selections"
  []
  (log/info "Starting to populate existing user feeds with new content")
  (doseq [user-record (dynamo-dao/retrieve-all-user-records)]
    (pre-populate-newsfeed user-record))
  (log/info "Finished populating existing user feeds with new content"))
