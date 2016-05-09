(ns com.pav.api.test.dbwrapper.user-test
  "Testing code to make sure we are writing to both tables same data."
  (:use midje.sweet)
  (:require [com.pav.api.test.utils.utils :refer [flush-sql-tables flush-selected-dynamo-tables]]
            [com.pav.api.dbwrapper.user :as u]
            [com.pav.api.test.utils.utils :as tu]
            [com.pav.api.domain.user :refer [assoc-common-attributes]]
            [com.pav.api.dbwrapper.helpers :refer [with-sql-backend-enabled]]
            [com.pav.api.dynamodb.db :refer [user-table-name]]
            [com.pav.api.dynamodb.user :as dynamodb]
            [com.pav.api.db.user :as sql]))

(defn select-values 
  "Return values in order for given keys. Removes nil."
  [mp ks]
  (remove nil?
          (reduce #(conj %1 (get mp %2)) [] ks)))

(with-sql-backend-enabled
  (against-background [(before :facts (do (flush-selected-dynamo-tables [user-table-name])
                                          (flush-sql-tables)))]

    (fact "Create a new user and validate data in both databases."
      (let [user (assoc-common-attributes (tu/new-pav-user))
            ret  (u/create-user user)
            dynamo-ret (dynamodb/get-user-by-id (:user_id ret))
            sql-ret    (sql/get-user-by-old-id (:user_id ret))]
        (map? ret) => true
        (select-values dynamo-ret [:user_id
                                   :email
                                   :first_name
                                   :last_name
                                   :img_url
                                   :gender
                                   :address
                                   :zipcode
                                   :state
                                   :lat
                                   :lng
                                   :public]) =>
        (select-values sql-ret [:old_user_id
                                :email
                                :first_name
                                :last_name
                                :img_url
                                :gender
                                :address
                                :zipcode
                                :state
                                :latitude
                                :longtitude
                                :public_profile])))
))
