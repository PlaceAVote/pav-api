(ns com.pav.api.resources.vote
  (:require [liberator.core :refer [resource defresource]]
            [com.pav.api.services.votes :as vs]
            [com.pav.api.services.users :as us]
            [com.pav.api.utils.utils :as utils]
            [com.pav.api.schema.vote :refer [new-vote-record-malformed?]]))

(defn retrieve-user-token [payload]
  (get-in payload [:request :identity]))

(defn valid-token? [payload]
  (contains? (retrieve-user-token payload) :user_id))

(defresource cast-vote
  :service-available? {:representation {:media-type "application/json"}}
  :available-media-types ["application/json"]
  :allowed-methods [:put]
  :authorized? (fn [ctx] (us/is-authenticated? (utils/retrieve-user-details ctx)))
  :malformed? (fn [ctx] (new-vote-record-malformed? (get-in ctx [:request :body])))
  :conflict? (fn [ctx] (vs/has-user-voted? (utils/retrieve-token-user-id ctx) (get-in ctx [:request :body :bill_id])))
  :put! (fn [ctx] (vs/create-user-vote-record (get-in ctx [:request :body]) (utils/retrieve-token-user-id ctx)))
  :handle-malformed {:error "Check payload is valid"}
  :handle-conflict  {:error "User has already voted on this issue"}
  :handle-created   {:message "Vote created successfully"}
  :handle-unauthorized {:error "Not Authorized"})

(defresource get-vote-count [bill-id]
  :service-available? {:representation {:media-type "application/json"}}
  :available-media-types ["application/json"]
  :allowed-methods [:get]
  :handle-ok (fn [_] (vs/get-vote-count bill-id)))

(defresource get-vote-records [bill-id]
  :service-available? {:representation {:media-type "application/json"}}
  :available-media-types ["application/json"]
  :allowed-methods [:get]
  :handle-ok (fn [_] (vs/get-votes-for-bill bill-id)))
