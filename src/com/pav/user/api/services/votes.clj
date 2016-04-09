(ns com.pav.user.api.services.votes
  (:require [com.pav.user.api.dynamodb.votes :as dv]
            [com.pav.user.api.dynamodb.user :as du]
            [com.pav.user.api.notifications.ws-handler :as ws]
            [com.pav.user.api.elasticsearch.user :refer [get-bill-info get-bill]])
  (:import (java.util UUID)))

(defn new-vote-count-record [{:keys [vote bill_id]}]
  (let [votes (if (true? vote)
                {:yes-count 1 :no-count 0}
                {:yes-count 0 :no-count 1})]
    (merge {:bill_id bill_id} votes)))

(defn create-vote-count
  "Create new vote count record"
  [new-record]
  (dv/create-vote-count new-record))

(defn update-vote-count
  "Update Vote Count"
  [bill_id vote]
  (dv/update-vote-count bill_id vote))

(defn publish-notification-event [evt]
  (du/add-event-to-user-notifications evt)
  (ws/publish-notification evt))

(defn- get-priority-bill-title [bill-info]
  (let [{:keys [official_title short_title featured_bill_title]} bill-info]
    (cond
      featured_bill_title featured_bill_title
      short_title short_title
      official_title official_title)))

(defn create-user-vote-record
  "Create new user vote relationship between a bill and user.  Also publish event on user timeline and notification feed."
  [{:keys [vote bill_id] :as record}]
  (let [bill-info (get-bill bill_id)
        vote-evt (merge (assoc record :type "vote" :event_id (.toString (UUID/randomUUID)))
                   :bill_title (get-priority-bill-title bill-info))
        notification-evt (assoc record :type "vote" :read false :notification_id (.toString (UUID/randomUUID)))
        current-count (dv/get-vote-count bill_id)]
    (dv/create-user-vote record)
    (if current-count
      (update-vote-count bill_id vote)
      (create-vote-count (new-vote-count-record record)))
    (du/add-event-to-usertimeline vote-evt)
    (publish-notification-event notification-evt)))

(defn get-user-vote
  "Retrieve user vote record using vote-id"
  [vote-id]
  (dv/get-user-vote vote-id))

(defn get-vote-count [bill_id]
  (let [count (dv/get-vote-count bill_id)]
    (if count
      count
      {:no-count 0 :yes-count 0})))

(defn has-user-voted? [user_id bill_id]
  (if (dv/get-user-bill-vote user_id bill_id)
    true
    false))

(defn get-votes-for-bill
  ([bill_id] (dv/get-votes-for-bill bill_id))
  ([bill_id user_id] (dv/get-user-vote bill_id user_id)))

(defn assoc-bill-vote-count [{:keys [bill_id] :as event}]
  (let [vcount (select-keys (get-vote-count bill_id) [:yes-count :no-count])]
    (if vcount
      (merge event vcount)
      (merge event {:yes-count 0 :no-count 0}))))
