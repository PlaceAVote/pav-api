(ns com.pav.api.test.redis.redis-test
  (:use midje.sweet)
  (:require [com.pav.api.test.utils.utils :refer [flush-redis]]
            [com.pav.api.redis.redis :refer [create-user-profile get-user-profile get-user-profile-by-email
                                             update-token]]))


(against-background [(before :facts (flush-redis))]
  (fact "Given a new user profile, persist use to redis"
    (let [new-user {:user_id  "user101" :first_name "John" :last_name "Rambo" :email "john@pl.com"
                    :public   false :registered false :token "token" :topics ["Arts"] :password "password"
                    :dob      "05/10/1987" :created_at 12312321 :country_code "USA" :state "NY" :lat "321321" :lng "213213"
                    :district "12" :address "Myaddress" :confirmation-token "101"}]
      (create-user-profile new-user)
      (get-user-profile "user101") => new-user
      (get-user-profile-by-email "john@pl.com") => new-user))

  (fact "Update user profile token"
    (let [new-user {:user_id  "user101" :first_name "John" :last_name "Rambo" :email "john@pl.com"
                    :public   false :registered false :token "token" :topics ["Arts"] :password "password"
                    :dob      "05/10/1987" :created_at 12312321 :country_code "USA" :state "NY" :lat "321321" :lng "213213"
                    :district "12" :address "Myaddress" :confirmation-token "101"}]
      (create-user-profile new-user)
      (update-token "user101" {:token "token2"})
      (get-user-profile "user101") => (assoc new-user :token "token2")))

  (fact "If user profile doesn't exist return nil"
    (get-user-profile "user102") => nil
    (get-user-profile-by-email "email@email.com") => nil))
