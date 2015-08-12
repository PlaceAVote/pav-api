(ns pav-user-api.test.user.user-test
  (:use midje.sweet)
  (:require [pav-user-api.handler :refer [app]]
            [pav-user-api.test.utils.utils :refer [test-user bootstrap-users bootstrap-constraints]]
            [ring.mock.request :refer [request body content-type]]
            [cheshire.core :as ch]))

(against-background [(before :facts (do (bootstrap-constraints)
                                      (bootstrap-users)))]
 (facts "Test cases for users"
   (fact "Get a list of existing users"
         (let [response (app (request :get "/user"))]
           (:status response) => 200
           (:body response) => (contains (ch/generate-string test-user) :in-any-order)))
   (fact "Create a new user, will return 201 status and newly created user"
         (let [response (app (content-type (request :put "/user" (ch/generate-string {:email "john@stuff.com" :password "stuff2"})) "application/json"))]
           (:status response) => 201
           (:body response) => (contains (ch/generate-string {:email "john@stuff.com" :password "stuff2"}))))
  (fact "Create a new user, with an existing email, should return 409"
        (let [_ (app (content-type (request :put "/user" (ch/generate-string {:email "john@stuff.com" :password "stuff2"})) "application/json"))
              response (app (content-type (request :put "/user" (ch/generate-string {:email "john@stuff.com" :password "stuff2"})) "application/json"))]
          (:status response) => 409))
   (fact "Retrieve a user by email"
         (let [response (app (request :get "/user/johnny@stuff.com"))]
           (:status response) => 200
           (:body response) => (contains (ch/generate-string test-user) :in-any-order)))
  (fact "Retrieve a user by email that doesn't exist"
        (let [response (app (request :get "/user/peter@stuff.com"))]
          (:status response) => 200
          (:body response) => ""))))

