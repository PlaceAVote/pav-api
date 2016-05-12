(ns com.pav.api.domain.comment
  (:require [schema.core :as s]
            [com.pav.api.schema.common :refer :all])
  (:import (java.util Date UUID)))

(s/defrecord NewDynamoBillComment
  [author       :- str-schema
   bill_id      :- str-schema
   body         :- str-schema
   id           :- str-schema
   comment_id   :- str-schema
   has_children :- s/Bool
   parent_id    :- (s/maybe str-schema)
   score        :- s/Int
   timestamp    :- Long
   deleted      :- s/Bool])

(s/defn ^:always-validate new-bill-comment :- NewDynamoBillComment
  "Create New Bill Comment for persisting to dynamoDB."
  [{:keys [parent_id] :or {parent_id nil} :as comment} user_info]
  (let [id (.toString (UUID/randomUUID))]
    (->
      (assoc comment :id id :comment_id id :author (:user_id user_info))
      (merge {:score 0 :timestamp (.getTime (Date.)) :has_children false :deleted false})
      map->NewDynamoBillComment)))

(comment
  (new-dynamo-bill-comment {:bill_id "hr2-114" :body "comment body" :parent_id "101"} {:user_id "110101"}))