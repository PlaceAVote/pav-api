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

(s/defrecord NewDynamoCommentScore
  [comment_id :- str-schema
   user_id    :- str-schema
   liked      :- s/Bool])

(s/defn ^:always-validate new-comment-score :- NewDynamoCommentScore
  "Create new comment score record for persistence to DynamoDB."
  [comment_id user_id liked]
  (map->NewDynamoCommentScore {:comment_id comment_id :user_id user_id :liked liked}))

(s/defrecord NewDynamoIssueComment
  [author       :- str-schema
   issue_id     :- str-schema
   body         :- str-schema
   comment_id   :- str-schema
   score        :- s/Int
   timestamp    :- Long
   updated_at   :- Long
   deleted      :- s/Bool])

(s/defn ^:always-validate new-issue-comment :- NewDynamoIssueComment
  [{:keys [issue_id body]} {:keys [user_id]}]
  (map->NewDynamoIssueComment {:comment_id (.toString (UUID/randomUUID))
                               :issue_id issue_id
                               :author user_id
                               :body body
                               :score 0
                               :timestamp (.getTime (Date.))
                               :updated_at (.getTime (Date.))
                               :deleted false}))