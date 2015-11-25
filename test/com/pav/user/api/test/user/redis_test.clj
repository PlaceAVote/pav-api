(ns com.pav.user.api.test.user.redis-test
  (:use midje.sweet)
  (:require [com.pav.user.api.test.utils.utils :refer [flush-redis]]
            [com.pav.user.api.redis.redis :refer [create-user-profile get-user-profile get-user-profile-by-email
                                                  update-token]]))


(against-background [(before :facts (flush-redis))]
  (fact "Given a new user profile, persist use to redis"
    (let [new-user {:user_id "user101" :first_name "John" :last_name "Rambo" :email "john@pl.com"
                    :public false :registered false :token "token" :topics ["Arts"] :password "password"
                    :dob "05/10/1987" :created_at 12312321 :country_code "USA"}]
      (create-user-profile new-user)
      (get-user-profile "user101") => new-user
      (get-user-profile-by-email "john@pl.com") => new-user))

  (fact "Update user profile token"
    (let [new-user {:user_id "user101" :first_name "John" :last_name "Rambo" :email "john@pl.com"
                    :public false :registered false :token "token" :topics ["Arts"] :password "password"
                    :dob "05/10/1987" :created_at 12312321 :country_code "USA"}]
      (create-user-profile new-user)
      (update-token new-user {:token "token2"})
      (get-user-profile "user101") => (assoc new-user :token "token2"))))
