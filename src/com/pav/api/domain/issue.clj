(ns com.pav.api.domain.issue
  (:require [schema.core :as s]
            [com.pav.api.schema.common :refer :all]
            [com.pav.api.utils.utils :refer [uuid->base64Str]]
            [environ.core :refer [env]])
  (:import (java.util UUID Date)))

(s/defrecord NewDynamoUserIssue
  [issue_id           :- str-schema
   short_issue_id     :- str-schema
   user_id            :- str-schema
   timestamp          :- Long
   negative_responses :- s/Num
   neutral_responses  :- s/Num
   positive_responses :- s/Num
   deleted            :- s/Bool
   comment            :- (s/maybe str-schema)
   bill_id            :- (s/maybe str-schema)
   bill_title         :- (s/maybe str-schema)
   article_link       :- (s/maybe str-schema)
   article_title      :- (s/maybe str-schema)
   article_img        :- (s/maybe str-schema)])

(defn new-issue-img-key [issue_id]
  (str "users/issues/images/" issue_id "/main.jpg"))

(defn assoc-issue-image [issue]
  (cond-> issue
    (:article_img issue) (update-in [:article_img]
                           (fn [_] (str (:cdn-url env)
                                     "/"
                                     (new-issue-img-key (:issue_id issue)))))))

(s/defn ^:always-validate new-user-issue :- NewDynamoUserIssue
  [issue-data graph-data user_id]
  (let [id (UUID/randomUUID)]
    (map->NewDynamoUserIssue
      (->>
        (select-keys issue-data [:bill_id :bill_title :comment])
        (merge
          {:issue_id (.toString id)
           :short_issue_id (uuid->base64Str id)
           :user_id user_id
           :timestamp (.getTime (Date.))
           :negative_responses 0
           :neutral_responses 0
           :positive_responses 0
           :deleted false})
        (merge (select-keys graph-data [:article_img :article_link :article_title]))
        assoc-issue-image))))
