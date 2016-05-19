(ns com.pav.api.test.dbwrapper.issue-test
  (:use midje.sweet)
  (:require [com.pav.api.test.utils.utils :as tu :refer [flush-sql-tables flush-selected-dynamo-tables select-values]]
            [com.pav.api.dbwrapper.helpers :refer [with-sql-backend-enabled]]
            [com.pav.api.dbwrapper.issue :as u]
            [com.pav.api.dynamodb.db :refer [user-table-name user-issues-table-name user-issue-responses-table-name]]
            [com.pav.api.dynamodb.user :as dynamodb]
            [com.pav.api.db.issue :as sql]
            [com.pav.api.domain.issue :refer [new-user-issue]]))

(with-sql-backend-enabled
  (against-background [(before :facts (do
                                        (flush-selected-dynamo-tables [user-table-name
                                                                       user-issues-table-name
                                                                       user-issue-responses-table-name])
                                        (flush-sql-tables)))]

    (facts "Test cases for Vote DBWrapper"

      (fact "Create a new bill comment associated with a user and validate data in both databases."
        (let [user (tu/create-user)
              issue_payload (new-user-issue
                              {:bill_id "hr2-114" :bill_title "bill title" :comment "issue comment"}
                              {:article_img "http://img.com" :article_link "http://link.com" :article_title "wolly"}
                              (:user_id user))
              new-issue (u/create-user-issue issue_payload)
              dynamo-ret (dynamodb/get-user-issue (:issue_id new-issue))
              sql-ret (sql/get-user-issue-by-old-id (:issue_id new-issue))]
          (map? new-issue) => true
          (select-values dynamo-ret [:issue_id
                                     :user_id
                                     :timestamp
                                     :timestamp
                                     :bill_id
                                     :article_img
                                     :article_link
                                     :article_title
                                     :comment
                                     :deleted]) =>
          (select-values sql-ret [:old_issue_id
                                  :old_user_id
                                  :created_at
                                  :updated_at
                                  :bill_id
                                  :article_img
                                  :article_link
                                  :article_title
                                  :comment
                                  :deleted])))

      (fact "Update Existing User Issue."
        (let [user (tu/create-user)
              issue_payload (new-user-issue
                              {:bill_id "hr2-114" :bill_title "bill title" :comment "issue comment"}
                              {:article_img "http://img.com" :article_link "http://link.com" :article_title "wolly"}
                              (:user_id user))
              new-issue (u/create-user-issue issue_payload)
              updated-issue (u/update-user-issue (:user_id user) (:issue_id new-issue) {:comment "new issue comment"})
              dynamo-ret (dynamodb/get-user-issue (:issue_id new-issue))
              sql-ret (sql/get-user-issue-by-old-id (:issue_id new-issue))]
          (map? updated-issue) => true
          (select-values dynamo-ret [:comment]) => (select-values sql-ret [:comment])))

      (fact "Mark Existing User Issue for deletion."
        (let [user (tu/create-user)
              issue_payload (new-user-issue
                              {:bill_id "hr2-114" :bill_title "bill title" :comment "issue comment"}
                              {:article_img "http://img.com" :article_link "http://link.com" :article_title "wolly"}
                              (:user_id user))
              new-issue (u/create-user-issue issue_payload)
              updated-issue (u/mark-user-issue-for-deletion (:user_id user) (:issue_id new-issue))
              dynamo-ret (dynamodb/get-user-issue (:issue_id new-issue))
              sql-ret (sql/get-user-issue-by-old-id (:issue_id new-issue))]
          (map? updated-issue) => true
          (select-values dynamo-ret [:deleted]) => (select-values sql-ret [:deleted])
          )))))
