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
                                  :old_parent_id])))

      (fact "Update Existing Comment, Then validate data in both datastores"
        (let [user (tu/create-user)
              comment-payload (new-bill-comment {:bill_id "hr2-114" :body "comment body"} user)
              new-comment (u/create-bill-comment comment-payload)
              update-ret (u/update-bill-comment (:comment_id comment-payload) {:body "updated comment body"})
              dynamo-ret (dynamodb/get-bill-comment (:comment_id new-comment))
              sql-ret (sql/get-bill-comment-by-old-id (:comment_id new-comment))]
          (map? update-ret) => true
          (select-values dynamo-ret [:body]) => (select-values sql-ret [:body])))

      (fact "Mark Existing Comment for deletion, Then validate data in both datastores"
        (let [user (tu/create-user)
              comment-payload (new-bill-comment {:bill_id "hr2-114" :body "comment body"} user)
              new-comment (u/create-bill-comment comment-payload)
              deleted-ret (u/mark-bill-comment-for-deletion (:comment_id comment-payload) (:user_id user))
              dynamo-ret (dynamodb/get-bill-comment (:comment_id new-comment))
              sql-ret (sql/get-bill-comment-by-old-id (:comment_id new-comment))]
          (map? deleted-ret) => true
          (select-values dynamo-ret [:deleted]) => (select-values sql-ret [:deleted])))

      (fact "Like Existing Comment, Then validate data is both datastores"
        (let [user (tu/create-user)
              comment-payload (new-bill-comment {:bill_id "hr2-114" :body "comment body"} user)
              new-comment (u/create-bill-comment comment-payload)
              _ (u/score-bill-comment (:comment_id comment-payload) (:user_id user) :like)
              ;;gather data from both tables
              dynamo-ret (dynamodb/get-bill-comment (:comment_id new-comment))
              sql-ret (sql/get-bill-comment-by-old-id (:comment_id new-comment))
              dynamo-score-evt (dynamodb/get-user-bill-comment-score (:comment_id new-comment) (:user_id user))
              sql-score-evt (sql/get-user-bill-comment-score (:id sql-ret) (:user_id sql-ret))]
          (select-values dynamo-ret [:score]) => (select-values sql-ret [:score])
          (select-values dynamo-score-evt [:liked
                                           :user_id
                                           :comment_id]) =>
          (select-values sql-score-evt [:liked
                                        :old_user_id
                                        :old_comment_id])))

      (fact "Dislike Existing Comment, Then validate data is both datastores"
        (let [user (tu/create-user)
              comment-payload (new-bill-comment {:bill_id "hr2-114" :body "comment body"} user)
              new-comment (u/create-bill-comment comment-payload)
              _ (u/score-bill-comment (:comment_id comment-payload) (:user_id user) :dislike)
              ;;gather data from both tables
              dynamo-ret (dynamodb/get-bill-comment (:comment_id new-comment))
              sql-ret (sql/get-bill-comment-by-old-id (:comment_id new-comment))
              dynamo-score-evt (dynamodb/get-user-bill-comment-score (:comment_id new-comment) (:user_id user))
              sql-score-evt (sql/get-user-bill-comment-score (:id sql-ret) (:user_id sql-ret))]
          (select-values dynamo-ret [:score]) => (select-values sql-ret [:score])
          (select-values dynamo-score-evt [:liked
                                           :user_id
                                           :comment_id]) =>
          (select-values sql-score-evt [:liked
                                        :old_user_id
                                        :old_comment_id])))

      (fact "Delete Users scoring record for existing Comment the user has liked, Then validate data is both datastores"
        (let [user (tu/create-user)
              comment-payload (new-bill-comment {:bill_id "hr2-114" :body "comment body"} user)
              new-comment (u/create-bill-comment comment-payload)
              _ (u/score-bill-comment (:comment_id comment-payload) (:user_id user) :like)
              _ (u/revoke-liked-bill-comment-score (:comment_id comment-payload) (:user_id user))
              ;;gather data from both tables
              dynamo-ret (dynamodb/get-bill-comment (:comment_id new-comment))
              sql-ret (sql/get-bill-comment-by-old-id (:comment_id new-comment))
              dynamo-score-evt (dynamodb/get-user-bill-comment-score (:comment_id new-comment) (:user_id user))
              sql-score-evt (sql/get-user-bill-comment-score (:id sql-ret) (:user_id sql-ret))]
          (select-values dynamo-ret [:score]) => (select-values sql-ret [:score])
          dynamo-score-evt => nil
          sql-score-evt => nil))

      (fact "Delete Users scoring record for existing Comment the user has disliked, Then validate data is both datastores"
        (let [user (tu/create-user)
              comment-payload (new-bill-comment {:bill_id "hr2-114" :body "comment body"} user)
              new-comment (u/create-bill-comment comment-payload)
              _ (u/score-bill-comment (:comment_id comment-payload) (:user_id user) :dislike)
              _ (u/revoke-disliked-bill-comment-score (:comment_id comment-payload) (:user_id user))
              ;;gather data from both tables
              dynamo-ret (dynamodb/get-bill-comment (:comment_id new-comment))
              sql-ret (sql/get-bill-comment-by-old-id (:comment_id new-comment))
              dynamo-score-evt (dynamodb/get-user-bill-comment-score (:comment_id new-comment) (:user_id user))
              sql-score-evt (sql/get-user-bill-comment-score (:id sql-ret) (:user_id sql-ret))]
          (select-values dynamo-ret [:score]) => (select-values sql-ret [:score])
          dynamo-score-evt => nil
          sql-score-evt => nil)))))
