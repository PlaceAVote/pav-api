(ns com.pav.api.resources.bill
  (:require [liberator.core :refer [resource defresource]]
            [com.pav.api.services.bills :as bs]
            [com.pav.api.services.comments :as cs]
            [com.pav.api.services.users :as us]
            [com.pav.api.utils.utils :as u]
            [schema.core :as s]))

(def CommentRecord
  {:bill_id s/Str
   :body s/Str})

(def ScoringRecord
  {:bill_id s/Str})

(def CommentRecordUpdate
  {:body s/Str})

(defn valid-payload? [schema payload]
  (if (nil? (s/check schema payload))
    false
    true))

(defn new-score-malformed? [payload]
  (valid-payload? ScoringRecord payload))

(defn new-comment-malformed? [payload]
  (valid-payload? CommentRecord payload))

(defn comment-update-malformed? [payload]
  (valid-payload? CommentRecordUpdate payload))

(defresource get-bill [bill_id]
  :allowed-methods [:get]
  :available-media-types ["application/json"]
  :exists? (fn [ctx] {:record (bs/get-bill bill_id (u/retrieve-token-user-id ctx))})
  :handle-ok :record)

(defresource get-trending-bills
  :allowed-methods [:get]
  :available-media-types ["application/json"]
  :handle-ok (bs/trending-bills))

(defresource create-comment [bill_id]
  :service-available? {:representation {:media-type "application/json"}}
  :authorized? (fn [ctx] (us/is-authenticated? (u/retrieve-user-details ctx)))
  :malformed? (fn [ctx] (new-comment-malformed? (get-in ctx [:request :body])))
  :allowed-methods [:put]
  :available-media-types ["application/json"]
  :put! (fn [ctx] (cs/create-bill-comment (u/retrieve-body ctx) (u/retrieve-user-details ctx)))
  :handle-malformed {:errors [{:body "Please specify a comment body and bill_id"}]}
  :handle-created :record)

(defresource update-comment [comment_id]
  :service-available? {:representation {:media-type "application/json"}}
  :authorized? (fn [ctx] (us/is-authenticated? (u/retrieve-user-details ctx)))
  :malformed? (fn [ctx] (comment-update-malformed? (get-in ctx [:request :body])))
  :allowed-methods [:post]
  :available-media-types ["application/json"]
  :post! (fn [ctx] {::record (cs/update-bill-comment (u/retrieve-body ctx) comment_id)})
  :handle-malformed {:errors [{:body "Please specify a comment body"}]}
  :handle-created ::record)

(defresource create-comment-reply [comment-id]
  :authorized? (fn [ctx] (us/is-authenticated? (u/retrieve-user-details ctx)))
  :malformed? (fn [ctx] (new-comment-malformed? (get-in ctx [:request :body])))
  :allowed-methods [:put]
  :available-media-types ["application/json"]
  :put! (fn [ctx] (cs/create-bill-comment-reply comment-id (u/retrieve-body ctx) (u/retrieve-user-details ctx)))
  :handle-created :record)

(defresource get-comments [bill_id]
  :allowed-methods [:get]
  :available-media-types ["application/json"]
  :handle-ok (fn [ctx] (cs/get-bill-comments (u/retrieve-token-user-id ctx) bill_id
                         :sort-by (keyword (get-in ctx [:request :params :sort-by]))
                         :last_comment_id (get-in ctx [:request :params :last_comment_id]))))

(defresource top-comments [bill-id]
  :allowed-methods [:get]
  :available-media-types ["application/json"]
  :handle-ok (fn [ctx] (cs/get-top-comments bill-id (u/retrieve-token-user-id ctx))))

(defresource like-comment [comment_id]
  :authorized? (fn [ctx] (us/is-authenticated? (u/retrieve-user-details ctx)))
  :malformed? (fn [ctx] (new-score-malformed? (get-in ctx [:request :body])))
  :allowed-methods [:post :delete]
  :available-media-types ["application/json"]
  :post! (fn [ctx] (cs/score-bill-comment (u/retrieve-token-user-id ctx) comment_id :like))
  :delete! (fn [ctx] (cs/revoke-liked-comment (u/retrieve-token-user-id ctx) comment_id))
  :handle-malformed "Comment is invalid"
  :handle-created :record)

(defresource dislike-comment [comment_id]
  :authorized? (fn [ctx] (us/is-authenticated? (u/retrieve-user-details ctx)))
  :malformed? (fn [ctx] (new-score-malformed? (get-in ctx [:request :body])))
  :allowed-methods [:post :delete]
  :available-media-types ["application/json"]
  :post! (fn [ctx] (cs/score-bill-comment (u/retrieve-token-user-id ctx) comment_id :dislike))
  :delete! (fn [ctx] (cs/revoke-disliked-comment (u/retrieve-token-user-id ctx) comment_id))
  :handle-malformed "Comment is invalid"
  :handle-created :record)
