(ns com.pav.user.api.test.user.elasticsearch-user-test
  (:use [midje.sweet])
  (:require [com.pav.user.api.elasticsearch.user :refer [index-user]]
            [com.pav.user.api.test.utils.utils :refer [flush-user-index]]))

(against-background [(before :facts (flush-user-index))]
                    (fact "Given a user profile, index user profile"
                          (let [user-profile {:user_id "user1" :email "john@pl.com" :first_name "John" :last_name "Rambo"
                                         :dob     "05/10/1984" :img_url "http://img.com"}
                                _ (index-user user-profile)])))
