(ns pav-user-api.test.user-test
  (:use midje.sweet)
  (:require [pav-user-api.handler :refer [app]]
            [pav-user-api.test.utils.utils :refer [test-user bootstrap-users]]
            [ring.mock.request :refer [request]]
            [cheshire.core :as ch]))

(against-background [(before :facts (do (bootstrap-users)))]
 (facts "Test cases for users"
   (fact "Confirm /user route exists"
         (let [response (app (request :get "/user"))]
           (:status response) => 200
           (:body response) => (contains (ch/generate-string test-user) :in-any-order)))))

