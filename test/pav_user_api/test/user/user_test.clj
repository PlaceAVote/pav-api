(ns pav-user-api.test.user.user-test
  (:use midje.sweet)
  (:require [pav-user-api.handler :refer [app]]
            [pav-user-api.test.utils.utils :refer [test-user bootstrap-users]]
            [ring.mock.request :refer [request]]
            [cheshire.core :as ch]))

(against-background [(before :facts (do (bootstrap-users)))]
 (facts "Test cases for users"
   (fact "Get a list of existing users"
         (let [response (app (request :get "/user"))]
           (:status response) => 200
           (:body response) => (contains (ch/generate-string test-user) :in-any-order)))
   (fact "Create a new user"
         (let [response (app (request :post "/user" {:email "john@stuff.com" :password "stuff2"}))]
           (:status response) => 201
           (:body response) (contains (ch/generate-string {:email "john@stuff.com" :password "stuff2"}))))
   (fact "Retrieve a user by email"
         (let [response (app (request :get "/user/johnny@stuff.com"))]
           (:status response) => 200
           (:body response) => (contains (ch/generate-string test-user) :in-any-order)))))

