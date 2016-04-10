(ns com.pav.api.resources.vote
  (:require [liberator.core :refer [resource defresource]]
            [com.pav.api.services.votes :as vs]
            [com.pav.api.services.users :as us]
            [com.pav.api.utils.utils :as utils]
            [com.pav.api.schema.vote :refer [new-vote-record-malformed?]])
  (:import (java.util UUID Date)))

(defn retrieve-user-token [payload]
  (get-in payload [:request :identity]))

(defn valid-token? [payload]
  (contains? (retrieve-user-token payload) :user_id))

(defn- map->json [m]
  (cheshire.core/generate-string m))

(defn new-vote-record [payload user_id]
  (-> payload
    (assoc :vote-id (.toString (UUID/randomUUID)))
    (assoc :created_at (.getTime (Date.)))
    (assoc :timestamp (.getTime (Date.)))
    (assoc :user_id user_id)))

(defresource cast-vote
  :available-media-types ["application/json"]
  :allowed-methods [:put]
  :authorized? (fn [ctx] (us/is-authenticated? (utils/retrieve-user-details ctx)))
  :malformed? (fn [ctx] (new-vote-record-malformed? (get-in ctx [:request :body])))
  :conflict? (fn [ctx] (vs/has-user-voted? (utils/retrieve-token-user-id ctx) (get-in ctx [:request :body :bill_id])))
  :put! (fn [ctx] (-> (new-vote-record (get-in ctx [:request :body]) (utils/retrieve-token-user-id ctx))
                      vs/create-user-vote-record))
  :handle-malformed (map->json {:error "Check payload is valid"})
  :handle-conflict (map->json {:error "User has already voted on this issue"})
  :handle-created (map->json {:message "Vote created successfully"}))

(defresource get-vote-count [bill-id]
  :available-media-types ["application/json"]
  :allowed-methods [:get]
  :handle-ok (fn [_] (vs/get-vote-count bill-id)))

(defresource get-vote-records [bill-id]
  :available-media-types ["application/json"]
  :allowed-methods [:get]
  :handle-ok (fn [_] (vs/get-votes-for-bill bill-id)))
