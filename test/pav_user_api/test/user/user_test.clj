(ns pav-user-api.test.user.user-test
  (:use midje.sweet)
  (:require [pav-user-api.handler :refer [app]]
            [pav-user-api.test.utils.utils :refer [test-user bootstrap-users bootstrap-constraints test-user-result]]
            [ring.mock.request :refer [request body content-type header]]
            [pav-user-api.models.user :refer [existing-user-error-msg login-error-msg]]
            [pav-user-api.services.users :refer [create-auth-token]]
            [cheshire.core :as ch]))

(against-background [(before :facts (do (bootstrap-constraints)
                                      (bootstrap-users)))]
 (facts "Test cases for users"
   (fact "Get a list of existing users"
         (let [{:keys [token]} (ch/parse-string (:body (app (content-type (request :put "/user" (ch/generate-string {:email "john@stuff.com" :password "stuff2"
                                                                                                                     :first_name "john" :last_name "stuff"
                                                                                                                     :country-code 840})) "application/json"))) true)
               response (app (header (request :get "/user") "PAV_AUTH_TOKEN" token))]
           (:status response) => 200
           (:body response) => (contains (ch/generate-string test-user-result) :in-any-order)))

    (fact "Get a list of existing users, without auth token, should return a 401"
        (let [response (app (request :get "/user"))]
          (:status response) => 401))

   (fact "Create a new user, will return 201 status and newly created user"
         (let [response (app (content-type (request :put "/user" (ch/generate-string {:email "john@stuff.com" :password "stuff2"
                                                                                      :first_name "john" :last_name "stuff"
                                                                                      :country-code 840})) "application/json"))]
           (:status response) => 201
           (keys (ch/parse-string (:body response) true)) => (contains [:token :email :first_name :last_name :country-code] :in-any-order)))

  (fact "Create a new user, with an existing email, should return 409"
        (let [_ (app (content-type (request :put "/user" (ch/generate-string {:email "john@stuff.com" :password "stuff2"
                                                                              :first_name "john" :last_name "stuff"
                                                                              :country-code 840})) "application/json"))
              response (app (content-type (request :put "/user" (ch/generate-string {:email "john@stuff.com" :password "stuff2"
                                                                                     :first_name "john" :last_name "stuff"
                                                                                     :country-code 840})) "application/json"))]
          (:status response) => 409
          (:body response ) => (ch/generate-string existing-user-error-msg)))

  (fact "Create a new user, when the payload is missing an email, return 400 with appropriate error message"
        (let [response (app (content-type (request :put "/user" (ch/generate-string {:password "stuff2"
                                                                                     :first_name "john" :last_name "stuff"
                                                                                     :country-code 840})) "application/json"))]
          (:status response) => 400
          (:body response ) => (ch/generate-string {:errors [{:email "A valid email address is a required"}]})))

  (fact "Create a new user, when the payload is missing a password, return 400 with appropriate error message"
        (let [response (app (content-type (request :put "/user" (ch/generate-string {:email "john@stuff.com"
                                                                                     :first_name "john" :last_name "stuff"
                                                                                     :country-code 840})) "application/json"))]
          (:status response) => 400
          (:body response ) => (ch/generate-string {:errors [{:password "Password is a required field"}]})))

  (fact "Create a new user, when the payload is empty, return 400 with appropriate error message"
        (let [response (app (content-type (request :put "/user" (ch/generate-string {})) "application/json"))]
          (:status response) => 400
          (:body response ) => (contains (ch/generate-string {:errors [
                                                              {:email "A valid email address is a required"}
                                                              {:password "Password is a required field"}
                                                              {:first_name "First Name is a required field"}
                                                              {:last_name "Last Name is a required field"}
                                                              {:country-code "Country Code is a required field.  Please Specify Country Code"}]}) :in-any-order)))

  (fact "Create a new user, when the email is invalid, return 400 with appropriate error message"
    (let [response (app (content-type (request :put "/user" (ch/generate-string {:email "johnstuffcom" :password "stuff2"
                                                                                 :first_name "john" :last_name "stuff"
                                                                                 :country-code 840})) "application/json"))]
      (:status response) => 400
      (:body response ) => (contains (ch/generate-string {:errors [{:email "A valid email address is a required"}]}) :in-any-order)))

  (fact "Create a new user, when the country is invalid, return 400 with appropriate error message"
        (let [response (app (content-type (request :put "/user" (ch/generate-string {:email "john@stuff.com" :password "stuff2"
                                                                                     :first_name "john" :last_name "stuff"
                                                                                     :country-code ""})) "application/json"))]
          (:status response) => 400
          (:body response ) => (contains (ch/generate-string {:errors [{:country-code "Country Code is a required field.  Please Specify Country Code"}]}) :in-any-order)))

  (fact "Create a new user, when the password is invalid, return 400 with appropriate error message"
      (let [response (app (content-type (request :put "/user" (ch/generate-string {:email "john@stuff.com" :password ""
                                                                                   :first_name "john" :last_name "stuff"
                                                                                   :country-code 840})) "application/json"))]
        (:status response) => 400
        (:body response ) => (contains (ch/generate-string {:errors [{:password "Password is a required field"}]}) :in-any-order)))

  (fact "Retrieve a user by email"
         (let [{:keys [token]} (ch/parse-string (:body (app (content-type (request :put "/user" (ch/generate-string {:email "john@stuff.com" :password "stuff2"
                                                                                                                     :first_name "john" :last_name "stuff"
                                                                                                                     :country-code 840})) "application/json"))) true)
               response (app (header (request :get "/user/john@stuff.com") "PAV_AUTH_TOKEN" token))]
           (:status response) => 200
           (:body response) => (contains (ch/generate-string {:email "john@stuff.com"}) :in-any-order)))

  (fact "Retrieve a user by email that doesn't exist"
        (let [{:keys [token]} (ch/parse-string (:body (app (content-type (request :put "/user" (ch/generate-string {:email "john@stuff.com" :password "stuff2"
                                                                                                                    :first_name "john" :last_name "stuff"
                                                                                                                    :country-code 840})) "application/json"))) true)
              response (app (header (request :get "/user/peter@stuff.com") "PAV_AUTH_TOKEN" token))]
          (:status response) => 200
          (:body response) => ""))

  (fact "Retrieve a user by email, without authentication token"
        (let [response (app (request :get "/user/johnny@stuff.com"))]
          (:status response) => 401))

  (fact "Create token for user when logging on"
        (let [_ (app (content-type (request :put "/user" (ch/generate-string {:email "john@stuff.com" :password "stuff2"
                                                                              :first_name "john" :last_name "stuff"
                                                                              :country-code 840})) "application/json"))
              login-response (app (content-type (request :post "/user/authenticate" (ch/generate-string {:email "john@stuff.com" :password "stuff2"})) "application/json"))]
          (:status login-response) => 201
          (keys (ch/parse-string (:body login-response) true)) => (contains [:token])))

  (fact "Create token for user that doesn't exist, returns 401 with suitable error message"
        (let [_ (app (content-type (request :put "/user" (ch/generate-string {:email "john@stuff.com" :password "stuff2"
                                                                              :first_name "john" :last_name "stuff"
                                                                              :country-code 840})) "application/json"))
              login-response (app (content-type (request :post "/user/authenticate" (ch/generate-string {:email "john@stuff.com" :password "invalid"})) "application/json"))]
          (:status login-response) => 401
          (:body login-response) => login-error-msg))

  (fact "Create token for user, when payload doesn't contain an email then returns 400 with suitable error message"
        (let [_ (app (content-type (request :put "/user" (ch/generate-string {:email "john@stuff.com" :password "stuff2"
                                                                              :first_name "john" :last_name "stuff"
                                                                              :country-code 840})) "application/json"))
              login-response (app (content-type (request :post "/user/authenticate" (ch/generate-string {:password "stuff2"})) "application/json"))]
          (:status login-response) => 400
          (:body login-response) => (ch/generate-string {:errors [{:email "A valid email address is a required"}]})))))

