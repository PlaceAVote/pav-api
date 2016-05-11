(ns com.pav.api.dynamodb.comments
  (:require [taoensso.faraday :as far]
            [com.pav.api.dynamodb.db :as dy]
            [com.pav.api.dynamodb.common :refer [batch-delete-from-feed]]
            [taoensso.truss :as t]
            [clojure.tools.logging :as log]
            [clojure.core.async :refer [go]])
  (:import (java.util Date)))

(defn create-bill-comment-thread-list-key [bill-id]
  (str "thread:" bill-id))

(defn get-comment-count [bill_id]
  (-> (far/query dy/client-opts dy/comment-details-table-name {:bill_id [:eq bill_id]} {:index "bill-comment-idx"})
      meta :count))

(defn get-issue-comment-count [issue_id]
  (-> (far/query dy/client-opts dy/user-issue-comments-table-name {:issue_id [:eq issue_id]} {:index "issueid-timestamp-idx"})
      meta :count))

(defn assoc-bill-comment-count
  "Associate a count of comments to the payload"
  [{:keys [bill_id] :as event}]
  (assoc event :comment_count (get-comment-count bill_id)))

(defn- associate-user-info [{:keys [author] :as comment}]
  (if-let [{i :img_url f :first_name l :last_name} (if author (far/get-item dy/client-opts dy/user-table-name {:user_id author}))]
    (assoc comment :author_img_url i :author_first_name f :author_last_name l)
    comment))

(defn associate-user-score [comment user_id]
  (if-let [scoring-record (and user_id (far/get-item dy/client-opts dy/comment-user-scoring-table
                                         {:comment_id (:comment_id comment) :user_id user_id}))]
    (if (:liked scoring-record)
      (assoc comment :liked true :disliked false)
      (assoc comment :liked false :disliked true))
    (assoc comment :liked false :disliked false)))

(defn- batch-get-bill-comments
  "Given a collection of bill_ids then collect associated comments"
  [comment-ids sort-key & [user_id]]
  (t/have (complement empty?) comment-ids)
  (->> ((keyword dy/comment-details-table-name)
         (far/batch-get-item dy/client-opts {dy/comment-details-table-name {:prim-kvs {:comment_id comment-ids}}}))
    (mapv #(cond-> % (:deleted %) (dissoc :body)))
    (mapv #(associate-user-score % user_id))
    (mapv #(associate-user-info %))
    (sort-by sort-key >)))

(defn get-bill-comments
  "Retrieve Parent level comments for bill-comment-idx"
  [bill_id sorted-by limit & {:keys [user_id last_comment_id]}]
  (t/have [:or keyword? nil?] sorted-by)
  (t/have integer? limit)
  (let [index (case sorted-by
                :highest-score ["bill-score-idx" :score]
                :latest ["bill-timestamp-idx" :timestamp]
                ["bill-score-idx" :score])
        last_comment (if last_comment_id
                       (first (far/query dy/client-opts dy/bill-comment-table-name {:comment_id [:eq last_comment_id]}
                                {:index "comment-idx" :limit 1 :span-reqs {:max 1}})))
        opts (merge
               {:index (first index) :order :desc :limit limit :span-reqs {:max 1}}
               ;;ugly hack for the sake of pagination.  Must find a better solution.
               (if last_comment
                 (case sorted-by
                   :highest-score {:last-prim-kvs (select-keys last_comment [:id :score :comment_id])}
                   :latest {:last-prim-kvs (select-keys last_comment [:id :timestamp :comment_id])}
                   {:last-prim-kvs (select-keys last_comment [:id :score :comment_id])})))
        parent-comments (far/query dy/client-opts dy/bill-comment-table-name {:id [:eq bill_id]} opts)
        comment_ids (->> parent-comments (mapv #(get-in % [:comment_id])))]
    (if-not (empty? comment_ids)
      {:comments (batch-get-bill-comments comment_ids (second index) user_id)
       :last_comment_id (get-in (meta parent-comments) [:last-prim-kvs :comment_id])}
      {:comments [] :last_comment_id nil})))

(defn retrieve-bill-comment-replies
  "If comment has children, then retrieve replies."
  [{:keys [has_children comment_id] :as comment} sort-by limit & [user_id]]
  (if has_children
    (assoc comment :replies (mapv #(retrieve-bill-comment-replies % sort-by limit user_id)
                              (:comments (get-bill-comments (create-bill-comment-thread-list-key comment_id) sort-by limit :user_id user_id))))
    (assoc comment :replies [])))

(defn create-bill-comment-reply [{:keys [parent_id comment_id timestamp score] :as comment}]
  (t/have integer? score)
  (t/have number? timestamp)
  ;update has_children attribute on parent
  (far/update-item dy/client-opts dy/comment-details-table-name {:comment_id parent_id}
    {:update-expr "SET #a = :b" :expr-attr-names {"#a" "has_children"} :expr-attr-vals {":b" true}})
  ;create reply message with :id=thread:parent_id also track the time of creation and current score for sorting purposes.
  (far/put-item dy/client-opts dy/bill-comment-table-name
    {:id         (create-bill-comment-thread-list-key parent_id)
     :comment_id comment_id :timestamp  timestamp :score score})
  (far/put-item dy/client-opts dy/comment-details-table-name comment))

(defn create-bill-comment [{:keys [parent_id bill_id comment_id timestamp score] :as comment}]
  (t/have integer? score)
  (t/have number? timestamp)
  (if (nil? parent_id)
    (do
      ;;create pointer to comment with :id=bill_id and also track the time of creation and current score for sorting purposes.
      (far/put-item dy/client-opts dy/bill-comment-table-name
        {:id bill_id :comment_id comment_id :timestamp timestamp :score score})
      (far/put-item dy/client-opts dy/comment-details-table-name comment)
      comment)
    (do
      (create-bill-comment-reply comment)
      comment)))

(defn update-bill-comment [new-body comment_id]
  (far/update-item dy/client-opts dy/comment-details-table-name {:comment_id comment_id}
    {:update-expr "SET #body = :body" :expr-attr-names {"#body" "body"} :expr-attr-vals {":body" new-body}
     :return :all-new}))

(defn- mark-bill-comment-for-deletion [comment_id]
  (far/update-item dy/client-opts dy/comment-details-table-name {:comment_id comment_id}
    {:update-expr     "SET #deleted = :val, #updated = :updated"
     :expr-attr-names {"#deleted" "deleted" "#updated" "updated_at"}
     :expr-attr-vals  {":val" true ":updated" (.getTime (Date.))}
     :return :all-new}))

(defn- remove-comment-from-timeline [user_id timestamp]
  (far/delete-item dy/client-opts dy/timeline-table-name {:user_id user_id :timestamp timestamp}))

(defn delete-comment-from-feed
  "Remove user issue from users newsfeed.  Can be expensive operation."
  [comment_id]
  (go
    (loop [comments (far/query dy/client-opts dy/userfeed-table-name {:comment_id [:eq comment_id]} {:index "commentid-idx"})]
      (when comments
        (batch-delete-from-feed (map #(select-keys % [:user_id :timestamp]) comments))
        (log/info (str "Deleting " (:count (meta comments)) " Comments for " comment_id " from " dy/userfeed-table-name)))
      (if (:last-prim-kvs (meta comments))
        (recur (far/query dy/client-opts dy/userfeed-table-name {:comment_id [:eq comment_id]}
                 {:index "commentid-idx" :last-prim-kvs (:last-prim-kvs (meta comments))}))))))

(declare get-bill-comment)
(defn delete-comment [comment_id user_id]
  (when-let [{:keys [timestamp]} (get-bill-comment comment_id)]
    (remove-comment-from-timeline user_id timestamp)
    (delete-comment-from-feed comment_id)
    (mark-bill-comment-for-deletion comment_id)))

(defn get-user-bill-comments
  "Retrieve comments for bill, if user_id is specified gather additional meta data."
  [id & {:keys [user_id sort-by limit last_comment_id] :or {sort-by :highest-score limit 10}}]
  (let [{:keys [comments last_comment_id]} (get-bill-comments id sort-by limit :user_id user_id :last_comment_id last_comment_id)
        comments (mapv #(retrieve-bill-comment-replies % sort-by limit user_id) comments)]
    {:total (count comments) :comments comments :last_comment_id last_comment_id}))

(defn get-bill-comment [comment_id]
  (far/get-item dy/client-opts dy/comment-details-table-name {:comment_id comment_id}))

(defn get-scoring-operation [operation]
  (case operation
    :like    {:update-expr "ADD #a :n" :expr-attr-names {"#a" "score"} :expr-attr-vals {":n" 1}}
    :dislike {:update-expr "ADD #a :n" :expr-attr-names {"#a" "score"} :expr-attr-vals {":n" -1}}))

(defn new-user-scoring-record [comment_id user_id operation]
  (case operation
    :like {:comment_id comment_id :user_id user_id :liked true}
    :dislike {:comment_id comment_id :user_id user_id :liked false}))

(defn score-comment [comment-id user_id operation]
  (let [op (get-scoring-operation operation)
        parent-comment-key (-> (far/query dy/client-opts dy/bill-comment-table-name {:comment_id [:eq comment-id]}
                                 {:index "comment-idx" :limit 1 :span-reqs {:max 1}}) first :id)]
    (far/update-item dy/client-opts dy/bill-comment-table-name {:id parent-comment-key :comment_id comment-id} op)
    (far/update-item dy/client-opts dy/comment-details-table-name {:comment_id comment-id} op)
    (far/put-item dy/client-opts dy/comment-user-scoring-table (new-user-scoring-record comment-id user_id operation))))

(defn get-top-comments
  "Retrieve top comments and user meta data for comments, user_id is optional."
  [bill-id & [user_id]]
  (let [bill-votes (far/query dy/client-opts dy/user-votes-table-name {:bill_id [:eq bill-id]}
                     {:index "bill-user-idx"
                      :return [:vote :user_id]})
        users-for     (into #{} (->> (filterv #(true? (:vote %)) bill-votes)
                                  (mapv :user_id)))
        users-against (into #{} (->> (filterv #(false? (:vote %)) bill-votes)
                                  (mapv :user_id)))
        parent-comments (:comments (get-bill-comments bill-id :score 10 :user_id user_id))
        comments-for     (filterv #(contains? users-for (:author %)) parent-comments)
        comments-against (filterv #(contains? users-against (:author %)) parent-comments)]

    (if (or (empty? comments-for) (empty? comments-against))
      {:for-comment     []
       :against-comment []}
      {:for-comment     (first comments-for)
       :against-comment (first comments-against)})))

(defn remove-liked-comment [user_id comment_id]
  (let [parent-comment-key (-> (far/query dy/client-opts dy/bill-comment-table-name {:comment_id [:eq comment_id]}
                                 {:index "comment-idx" :limit 1 :span-reqs {:max 1}}) first :id)
        op {:update-expr "ADD #a :n" :expr-attr-names {"#a" "score"} :expr-attr-vals {":n" -1}}]
    (far/update-item dy/client-opts dy/bill-comment-table-name {:id parent-comment-key :comment_id comment_id} op)
    (far/update-item dy/client-opts dy/comment-details-table-name {:comment_id comment_id} op)
    (far/delete-item dy/client-opts dy/comment-user-scoring-table {:comment_id comment_id :user_id user_id})))

(defn remove-disliked-comment [user_id comment_id]
  (let [parent-comment-key (-> (far/query dy/client-opts dy/bill-comment-table-name {:comment_id [:eq comment_id]}
                                 {:index "comment-idx" :limit 1 :span-reqs {:max 1}}) first :id)
        op {:update-expr "ADD #a :n" :expr-attr-names {"#a" "score"} :expr-attr-vals {":n" 1}}]
    (far/update-item dy/client-opts dy/bill-comment-table-name {:id parent-comment-key :comment_id comment_id} op)
    (far/update-item dy/client-opts dy/comment-details-table-name {:comment_id comment_id} op)
    (far/delete-item dy/client-opts dy/comment-user-scoring-table {:comment_id comment_id :user_id user_id})))

(defn comment-count-between [start end]
  (->
    (far/scan dy/client-opts dy/comment-details-table-name
      {:attr-conds {:timestamp [:between [start end]]}})
    meta
    :count))

(defn create-issue-comment [comment]
  (far/put-item dy/client-opts dy/user-issue-comments-table-name comment))

(defn update-user-issue-comment [new-body comment_id]
  (far/update-item dy/client-opts dy/user-issue-comments-table-name {:comment_id comment_id}
    {:update-expr     "SET #body = :body, #updated = :updated"
     :expr-attr-names {"#body" "body" "#updated" "updated_at"}
     :expr-attr-vals  {":body" new-body ":updated" (.getTime (Date.))}
     :return :all-new}))

(defn get-issue-comment [comment_id]
  (far/get-item dy/client-opts dy/user-issue-comments-table-name {:comment_id comment_id}))

(defn- assoc-issue-comment-score [{:keys [comment_id] :as comment} user_id]
  (let [{liked? :liked} (and user_id (far/get-item dy/client-opts dy/user-issue-comments-scoring-table
                                      {:comment_id comment_id :user_id user_id}))]
    (cond-> comment
      (nil? liked?)   (assoc :liked false :disliked false)
      (true? liked?)  (assoc :liked true)
      (false? liked?) (assoc :disliked false))))

(defn- assoc-user-issue-comment-scores [user_id comments]
  (if user_id
    (mapv #(assoc-issue-comment-score % user_id) comments)
    (mapv #(assoc % :liked false :disliked false) comments)))

(defn get-user-issue-comments
  "Retrieve user issue comments"
  [issue_id & {:keys [user_id sort-by limit last_comment_id]
               :or {sort-by :highest-score limit 10}}]
  (let [index (case sort-by
                :highest-score "issueid-score-idx"
                :latest "issueid-timestamp-idx"
                "issueid-score-idx")
        opts (merge {:index index :limit limit :order :desc :span-reqs {:max 1}}
               (when-let [last_comment (and last_comment_id (get-issue-comment last_comment_id))]
                 (case sort-by
                   :highest-score {:last-prim-kvs (select-keys last_comment [:issue_id :score :comment_id])}
                   :latest {:last-prim-kvs (select-keys last_comment [:issue_id :timestamp :comment_id])}
                   {:last-prim-kvs (select-keys last_comment [:issue_id :score :comment_id])})))
        comments (far/query dy/client-opts dy/user-issue-comments-table-name {:issue_id [:eq issue_id]} opts)]
    {:total           (count comments)
     :comments        (assoc-user-issue-comment-scores user_id comments)
     :last_comment_id (:comment_id (:last-prim-kvs (meta comments)))}))

(defn mark-user-issue-for-deletion [comment_id]
  (far/update-item dy/client-opts dy/user-issue-comments-table-name {:comment_id comment_id}
    {:update-expr     "SET #deleted = :deleted, #updated = :updated"
     :expr-attr-names {"#deleted" "deleted" "#updated" "updated_at"}
     :expr-attr-vals  {":deleted" true ":updated" (.getTime (Date.))}
     :return :all-new}))

(defn score-issue-comment [comment_id user_id operation]
  (let [op (get-scoring-operation operation)]
    (far/put-item dy/client-opts dy/user-issue-comments-scoring-table (new-user-scoring-record comment_id user_id operation))
    (far/update-item dy/client-opts dy/user-issue-comments-table-name {:comment_id comment_id} (merge op {:return :all-new}))))

(defn revoke-issue-score [user_id comment_id operation]
  (let [op (get-scoring-operation operation)]
    (far/delete-item dy/client-opts dy/user-issue-comments-scoring-table {:comment_id comment_id :user_id user_id})
    (far/update-item dy/client-opts dy/user-issue-comments-table-name {:comment_id comment_id} (merge op {:return :all-new}))))