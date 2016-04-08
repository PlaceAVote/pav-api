(ns com.pav.user.api.dynamodb.comments
  (:require [taoensso.faraday :as far]
            [com.pav.user.api.dynamodb.db :as dy]
            [taoensso.truss :as t]))

(defn create-bill-comment-thread-list-key [bill-id]
  (str "thread:" bill-id))

(defn assoc-bill-comment-count
  "Associate a count of comments to the payload"
  [{:keys [bill_id] :as event}]
  (let [ccount (count (far/query dy/client-opts dy/comment-details-table-name {:bill_id [:eq bill_id]} {:index "bill-comment-idx"}))]
    (assoc event :comment_count ccount)))

(defn- associate-user-img [{:keys [author] :as comment}]
  (if-let [{img :img_url} (if author (far/get-item dy/client-opts dy/user-table-name {:user_id author}))]
    (assoc comment :author_img_url img)
    comment))

(defn- associate-user-vote [comment user_id]
  (if-let [scoring-record (and user_id (far/get-item dy/client-opts dy/comment-user-scoring-table
                                         {:comment_id (:comment_id comment) :user_id user_id}))]
    (if (:liked scoring-record)
      (assoc comment :liked true :disliked false)
      (assoc comment :liked false :disliked true))
    (assoc comment :liked false :disliked false)))

(defn- batch-get-comments
  "Given a collection of bill_ids then collect associated comments"
  [comment-ids sort-key & [user_id]]
  (t/have (complement empty?) comment-ids)
  (->> ((keyword dy/comment-details-table-name)
         (far/batch-get-item dy/client-opts {dy/comment-details-table-name {:prim-kvs {:comment_id comment-ids}}}))
    (mapv #(associate-user-vote % user_id))
    (mapv #(associate-user-img %))
    (sort-by sort-key >)))

(defn get-bill-comments-ids
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
        comment_ids (->> (far/query dy/client-opts dy/bill-comment-table-name {:id [:eq bill_id]} opts)
                      (mapv #(get-in % [:comment_id])))]
    (if-not (empty? comment_ids)
      (batch-get-comments comment_ids (second index) user_id)
      [])))

(defn retrieve-replies
  "If comment has children, then retrieve replies."
  [{:keys [has_children comment_id] :as comment} sort-by limit & [user_id]]
  (if has_children
    (assoc comment :replies (mapv #(retrieve-replies % sort-by limit user_id)
                              (get-bill-comments-ids
                                (create-bill-comment-thread-list-key comment_id) sort-by limit :user_id user_id)))
    (assoc comment :replies [])))

(defn create-reply [{:keys [parent_id comment_id timestamp score] :as comment}]
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

(defn create-comment [{:keys [parent_id bill_id comment_id timestamp score] :as comment}]
  (t/have integer? score)
  (t/have number? timestamp)
  (if (nil? parent_id)
    (do
      ;;create pointer to comment with :id=bill_id and also track the time of creation and current score for sorting purposes.
      (far/put-item dy/client-opts dy/bill-comment-table-name
        {:id bill_id :comment_id comment_id :timestamp timestamp :score score})
      (far/put-item dy/client-opts dy/comment-details-table-name comment))
    (create-reply comment)))

(defn get-comments
  "Retrieve comments for bill, if user_id is specified gather additional meta data."
  [id & {:keys [user_id sort-by limit last_comment_id] :or {sort-by :highest-score limit 10}}]
  (let [comments (->> (get-bill-comments-ids id sort-by limit :user_id user_id :last_comment_id last_comment_id)
                   (mapv #(retrieve-replies % sort-by limit user_id)))]
    {:total (count comments) :comments comments :last_comment_id (:comment_id (last comments))}))

(defn get-comment [comment_id]
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
        parent-comments  (get-bill-comments-ids bill-id :score 10 :user_id user_id)
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
