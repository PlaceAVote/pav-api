(ns com.pav.api.test.user.validate-user-test
  (:use midje.sweet)
  (:require [com.pav.api.handler :refer [app]]
            [com.pav.api.test.utils.utils :refer [make-request parse-response-body
                                                  flush-dynamo-tables
                                                  flush-redis
                                                  flush-es-indexes
                                                  bootstrap-bills-and-metadata
                                                  test-user
                                                  test-fb-user
                                                  pav-req]]
            [ring.mock.request :refer [request body content-type header]]
            [com.pav.api.resources.user :refer [existing-user-error-msg login-error-msg]]
            [com.pav.api.authentication.authentication :refer [create-auth-token]]
            [cheshire.core :as ch]))

(against-background [(before :facts (do
                                      (flush-dynamo-tables)
                                      (flush-redis)))]

  (facts "Test Cases to cover the validation of user properties during onboarding process"

    (fact "Create two new users, When the second user attempts to use the same email, Then throw 400 Error
           with suitable message"
      (pav-req :put "/user" test-user)
      (let [{status :status body :body} (pav-req :post "/user/validate" (select-keys test-user [:email]))
            errors (ch/parse-string body true)]
        status => 400
        errors => {:errors [{:email "This email is currently in use."}]}))

    (fact "Create two new users, When the second users email is valid, Then return 200"
      (pav-req :put "/user" test-user)
      (let [{status :status} (pav-req :post "/user/validate" (assoc test-user :email "random@placeavote.com"))]
        status => 200))

    (fact "Create two new users, When an unrecognised parameter is provided, Then ignore and return 200"
      (pav-req :put "/user" test-user)
      (let [{status :status} (pav-req :post "/user/validate" {:crap "equals crap"})]
        status => 200))))


