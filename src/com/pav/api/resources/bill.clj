(ns com.pav.api.resources.bill
  (:require [liberator.core :refer [resource defresource]]
            [liberator.representation :refer [as-response]]
            [com.pav.api.services.bills :as bs]
            [com.pav.api.services.comments :as cs]
            [com.pav.api.services.users :as us]
            [com.pav.api.utils.utils :as u]
            [com.pav.api.schema.comment :refer :all]))

(defresource get-bill [bill_id]
  :service-available? {:representation {:media-type "application/json"}}
  :allowed-methods [:get]
  :available-media-types ["application/json"]
  :exists? (fn [ctx] {:record (bs/get-bill bill_id (u/retrieve-token-user-id ctx))})
  :handle-ok :record)

(defresource get-trending-bills
  :service-available? {:representation {:media-type "application/json"}}
  :allowed-methods [:get]
  :available-media-types ["application/json"]
  :handle-ok (bs/trending-bills))

(defresource create-comment [bill_id]
  :service-available? {:representation {:media-type "application/json"}}
  :authorized? (fn [ctx] (us/is-authenticated? (u/retrieve-user-details ctx)))
  :malformed? (fn [ctx] (new-bill-comment-malformed? (get-in ctx [:request :body])))
  :allowed-methods [:put]
  :available-media-types ["application/json"]
  :put! (fn [ctx] (cs/create-bill-comment (u/retrieve-body ctx) (u/retrieve-user-details ctx)))
  :handle-malformed {:errors [{:body "Please specify a comment body and bill_id"}]}
  :handle-created (fn [{record :record}] (cheshire.core/generate-string record))
  :handle-unauthorized {:error "Not Authorized"})

(defresource update-comment [comment_id]
  :service-available? {:representation {:media-type "application/json"}}
  :authorized? (fn [ctx] (and (us/is-authenticated? (u/retrieve-user-details ctx))
                              (cs/is-author? comment_id (u/retrieve-token-user-id ctx))))
  :malformed? (fn [ctx] (bill-comment-update-malformed? (get-in ctx [:request :body])))
  :allowed-methods [:post]
  :available-media-types ["application/json"]
  :post! (fn [ctx] {::record (cs/update-bill-comment (u/retrieve-body ctx) comment_id)})
  :handle-malformed {:errors [{:body "Please specify a comment body"}]}
  :handle-created ::record
  :handle-unauthorized {:error "Not Authorized"})

(defresource delete-comment [comment_id]
  :service-available? {:representation {:media-type "application/json"}}
  :authorized? (fn [ctx] (and (us/is-authenticated? (u/retrieve-user-details ctx))
                              (cs/is-author? comment_id (u/retrieve-token-user-id ctx))))
  :allowed-methods [:delete]
  :available-media-types ["application/json"]
  :delete! (fn [ctx] {::record (cs/delete-bill-comment comment_id (u/retrieve-token-user-id ctx) )})
  :handle-unauthorized {:error "Not Authorized"})

(defresource create-comment-reply [comment-id]
  :service-available? {:representation {:media-type "application/json"}}
  :authorized? (fn [ctx] (and
                           (us/is-authenticated? (u/retrieve-user-details ctx))
                           (cs/bill-comment-exists? comment-id)))
  :malformed? (fn [ctx] (new-bill-comment-malformed? (get-in ctx [:request :body])))
  :allowed-methods [:put]
  :available-media-types ["application/json"]
  :put! (fn [ctx] (cs/create-bill-comment comment-id (u/retrieve-body ctx) (u/retrieve-user-details ctx)))
  :handle-created (fn [{record :record}] (cheshire.core/generate-string record))
  :handle-malformed {:errors [{:body "Please specify a comment body and bill_id"}]}
  :handle-unauthorized {:error "Please check authorization token and comment_id is a valid one."})

(defresource get-comments [bill_id]
  :service-available? {:representation {:media-type "application/json"}}
  :allowed-methods [:get]
  :available-media-types ["application/json"]
  :handle-ok (fn [ctx] (cs/get-bill-comments (u/retrieve-token-user-id ctx) bill_id
                         :sort-by (keyword (get-in ctx [:request :params :sort-by]))
                         :last_comment_id (get-in ctx [:request :params :last_comment_id]))))

(defresource top-comments [bill-id]
  :service-available? {:representation {:media-type "application/json"}}
  :allowed-methods [:get]
  :available-media-types ["application/json"]
  :handle-ok (fn [ctx] (cs/get-top-comments bill-id (u/retrieve-token-user-id ctx))))

(defresource like-comment [comment_id]
  :service-available? {:representation {:media-type "application/json"}}
  :authorized? (fn [ctx] (and
                           (us/is-authenticated? (u/retrieve-user-details ctx))
                           (cs/bill-comment-exists? comment_id)))
  :malformed? (fn [ctx] (new-bill-score-malformed? (get-in ctx [:request :body])))
  :allowed-methods [:post :delete]
  :available-media-types ["application/json"]
  :post! (fn [ctx] (cs/score-bill-comment (u/retrieve-token-user-id ctx) comment_id :like))
  :delete! (fn [ctx] (cs/revoke-liked-comment (u/retrieve-token-user-id ctx) comment_id))
  :handle-malformed {:error "Payload contains invalid bill_id"}
  :handle-created :record
  :handle-unauthorized {:error "Please check authorization token and comment_id is a valid one."})

(defresource dislike-comment [comment_id]
  :service-available? {:representation {:media-type "application/json"}}
  :authorized? (fn [ctx] (and
                           (us/is-authenticated? (u/retrieve-user-details ctx))
                           (cs/bill-comment-exists? comment_id)))
  :malformed? (fn [ctx] (new-bill-score-malformed? (get-in ctx [:request :body])))
  :allowed-methods [:post :delete]
  :available-media-types ["application/json"]
  :post! (fn [ctx] (cs/score-bill-comment (u/retrieve-token-user-id ctx) comment_id :dislike))
  :delete! (fn [ctx] (cs/revoke-disliked-comment (u/retrieve-token-user-id ctx) comment_id))
  :handle-malformed {:error "Payload contains invalid bill_id"}
  :handle-created :record
  :handle-unauthorized {:error "Please check authorization token and comment_id is a valid one."})
