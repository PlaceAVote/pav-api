(ns com.pav.api.test.user.user-invite-test
  (:use midje.sweet)
  (:require [com.pav.api.test.utils.utils :refer [flush-dynamo-tables
                                                  flush-sql-tables
                                                  flush-redis
                                                  flush-es-indexes
                                                  bootstrap-bills-and-metadata
                                                  pav-req
                                                  new-pav-user
                                                  new-fb-user]]
            [com.pav.api.resources.user :refer [existing-user-error-msg login-error-msg]]))

(against-background [(before :facts (do (flush-dynamo-tables)
                                        (flush-redis)
                                        (flush-es-indexes)
                                        (flush-sql-tables)
                                        (bootstrap-bills-and-metadata)))]
  (facts "User Invites"
    (fact "Given a new user, When they invite a user, Then return 201"
      (let [payload {:message "my message" :contacts [{:name "Keith" :email "keith@placeavote.com"}]}
            {body :body} (pav-req :put "/user" (new-pav-user))
            token (:token body)
            {status :status} (pav-req :post "/user/invite" token payload)]
        status => 201))))
