(ns com.pav.api.test.user.validate-user-test
  (:use midje.sweet)
  (:require [com.pav.api.handler :refer [app]]
            [com.pav.api.test.utils.utils :refer [flush-dynamo-tables
                                                  flush-redis
                                                  pav-req
                                                  new-pav-user]]))

(against-background [(before :contents (do (flush-dynamo-tables) (flush-redis)))]

  (facts "Test Cases to cover the validation of user properties during onboarding process"

    (fact "Create two new users, When the second user attempts to use the same email, Then throw 400 Error
           with suitable message"
      (let [user (new-pav-user)
            _ (pav-req :put "/user" user)
            {status :status errors :body} (pav-req :post "/user/validate" (select-keys user [:email]))]
        status => 400
        errors => {:errors [{:email "This email is currently in use."}]}))

    (fact "Create two new users, When an unrecognised parameter is provided, Then return 400"
      (let [user (new-pav-user)
            _ (pav-req :put "/user" user)
            {status :status errors :body} (pav-req :post "/user/validate" {:crap "equals crap"})]
        status => 400
        errors => {:errors [{:email "A valid email address is a required"} {:crap "field is unknown"}]}))

    (fact "Create two new users, When the second users email is valid, Then return 200"
      (let [user (new-pav-user)
            _ (pav-req :put "/user" user)
            {status :status} (pav-req :post "/user/validate" {:email "random@placeavote.com"})]
        status => 200))))


