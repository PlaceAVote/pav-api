(ns com.pav.api.test.dbwrapper.user-test
  "Testing code to make sure we are writing to both tables same data."
  (:use midje.sweet)
  (:require [com.pav.api.test.utils.utils :refer [flush-sql-tables flush-selected-dynamo-tables]]
            [com.pav.api.dbwrapper.user :as u]
            [com.pav.api.test.utils.utils :as tu]
            [com.pav.api.domain.user :refer [assoc-common-attributes]]
            [com.pav.api.dynamodb.db :refer [user-table-name]]))

(against-background [(before :facts (do (flush-selected-dynamo-tables [user-table-name])
                                        (flush-sql-tables)))]
  (fact "Create a new user and validate data in both databases."
    (let [user (assoc-common-attributes (tu/new-pav-user))
          ret  (u/create-user user)]
      (map? ret) => true)))
