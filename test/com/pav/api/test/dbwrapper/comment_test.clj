(ns com.pav.api.test.dbwrapper.comment-test
  (:use midje.sweet)
  (:require [com.pav.api.test.utils.utils :refer [flush-sql-tables flush-selected-dynamo-tables]]
            [com.pav.api.test.utils.utils :as tu :refer [select-values]]
            [com.pav.api.dbwrapper.helpers :refer [with-sql-backend-enabled]]
            [com.pav.api.dbwrapper.comment :as u]
            [com.pav.api.dynamodb.db :refer [user-table-name bill-comment-table-name comment-details-table-name
                                             comment-user-scoring-table]]
            [com.pav.api.dynamodb.comments :as dynamodb]
            [com.pav.api.db.comment :as sql]
            [com.pav.api.domain.comment :refer [new-bill-comment]]))

(with-sql-backend-enabled
  (against-background [(before :facts (do
                                        (flush-selected-dynamo-tables [user-table-name
                                                                       bill-comment-table-name
                                                                       comment-details-table-name
                                                                       comment-user-scoring-table])
                                          (flush-sql-tables)))]

    (facts "Test cases to ensure data consistency between dynamodb and mysql"

      (fact "Create a new bill comment associated with a user and validate data in both databases."
        (let [user (tu/create-user)
              comment-payload (new-bill-comment {:bill_id "hr2-114" :body "comment body"} user)
              new-comment (u/create-bill-comment comment-payload)
              dynamo-ret (dynamodb/get-bill-comment (:comment_id new-comment))
              sql-ret (sql/get-bill-comment-by-old-id (:comment_id new-comment))]
          (map? new-comment) => true
          (select-values dynamo-ret [:author
                                     :comment_id
                                     :body
                                     :timestamp
                                     :timestamp
                                     :has_children
                                     :score
                                     :deleted
                                     :parent_id]) =>
          (select-values sql-ret [:old_user_id
                                  :old_comment_id
                                  :body
                                  :created_at
                                  :updated_at
                                  :has_children
                                  :score
                                  :deleted
                                  :old_parent_id])))

      (fact "Reply to an existing comment and validate the comment reply data in both databases."
        (let [user (tu/create-user)
              parent_comment (u/create-bill-comment (new-bill-comment {:bill_id "hr2-114" :body "comment body"} user))
              new-reply (u/create-bill-comment (assoc
                                                 (new-bill-comment {:bill_id "hr2-114" :body "comment body"} user)
                                                 :parent_id (:comment_id parent_comment)))
              dynamo-ret (dynamodb/get-bill-comment (:comment_id new-reply))
              sql-ret (sql/get-bill-comment-by-old-id (:comment_id new-reply))]
          (println "dynamo " dynamo-ret "\nmysql " sql-ret)
          (map? new-reply) => true
          (:parent_id new-reply) => (:comment_id parent_comment)
          (select-values dynamo-ret [:author
                                     :comment_id
                                     :body
                                     :timestamp
                                     :timestamp
                                     :has_children
                                     :score
                                     :deleted
                                     :parent_id]) =>
          (select-values sql-ret [:old_user_id
                                  :old_comment_id
                                  :body
                                  :created_at
                                  :updated_at
                                  :has_children
                                  :score
                                  :deleted
                                  :old_parent_id]))))))
