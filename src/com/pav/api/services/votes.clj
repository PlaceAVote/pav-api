(ns com.pav.api.services.votes
  (:require [com.pav.api.dynamodb.votes :as dv]
            [com.pav.api.elasticsearch.user :refer [get-bill-info get-bill get-priority-bill-title]]
            [com.pav.api.events.handler :refer [process-event]]
            [com.pav.api.events.vote :refer [create-timeline-vote-event
                                             create-notification-vote-event]]
            [clojure.tools.logging :as log])
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

(defn- publish-vote-events [{:keys [bill_id] :as vote-record}]
  (try
    (process-event (create-timeline-vote-event (.toString (UUID/randomUUID)) vote-record))
    (process-event (->> (get-priority-bill-title (get-bill bill_id))
                        (assoc vote-record :bill_title)
                        create-notification-vote-event))
    (catch Exception e (log/error (str "Error occured publishing vote events for " vote-record) e))))

(defn create-user-vote-record
  "Create new user vote relationship between a bill and user.  Also publish event on user timeline and notification feed."
  [{:keys [vote bill_id] :as record}]
  (let [current-count (dv/get-vote-count bill_id)]
    (dv/create-user-vote record)
    (if current-count
      (update-vote-count bill_id vote)
      (create-vote-count (new-vote-count-record record)))
    (publish-vote-events record)))

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
