(ns com.pav.user.api.test.user.user-test
  (:use midje.sweet)
  (:require [com.pav.user.api.handler :refer [app]]
            [com.pav.user.api.test.utils.utils :refer [make-request parse-response-body
                                                       create-user-table
                                                       delete-user-table]]
            [ring.mock.request :refer [request body content-type header]]
            [com.pav.user.api.resources.user :refer [existing-user-error-msg login-error-msg]]
            [com.pav.user.api.services.users :refer [create-auth-token]]
            [cheshire.core :as ch]))

(against-background [(before :facts (do
                                      (delete-user-table)
                                      (create-user-table)
                                      ))]

   (fact "Create a new user, will return 201 status and newly created user"
         (let [response (app (content-type (request :put "/user" (ch/generate-string {:email "john@stuff.com" :password "stuff2"
                                                                                      :first_name "john" :last_name "stuff"
                                                                                      :dob "05/10/1984"
                                                                                      :country_code "USA"
                                                                                      :topics ["Defence" "Arts"]})) "application/json"))]
           (:status response) => 201
           (keys (ch/parse-string (:body response) true)) => (contains [:token :email :first_name :last_name :dob :country_code
                                                                        :topics :created_at] :in-any-order)))

   (fact "Create a new user from facebook login, will return 201 status and newly created user profile"
         (let [response (app (content-type (request :put "/user/facebook" (ch/generate-string {:email "paul@facebook.com"
                                                                                               :first_name "john" :last_name "stuff"
                                                                                               :dob "05/10/1984"
                                                                                               :country_code "USA"
                                                                                               :img_url "http://image.com/image.jpg"
                                                                                               :topics ["Defence" "Arts"]
                                                                                               :token "token"})) "application/json"))]
           (:status response) => 201
           (keys (ch/parse-string (:body response) true)) => (contains [:email :first_name :last_name :dob :country_code
                                                                        :img_url :topics :token :created_at] :in-any-order)))


  (fact "Create a new user from facebook login, when email is missing, return 400 with appropriate error message"
        (let [response (app (content-type (request :put "/user/facebook" (ch/generate-string {
                                                                                              :first_name "john" :last_name "stuff"
                                                                                              :dob "05/10/1984"
                                                                                              :country_code "USA"
                                                                                              :img_url "http://image.com/image.jpg"
                                                                                              :topics ["Defence" "Arts"]
                                                                                              :token "token"})) "application/json"))]
          (:status response) => 400
          (:body response ) => (ch/generate-string {:errors [{:email "A valid email address is a required"}]})))

  (fact "Create a new user from facebook login, when token is missing, return 400 with appropriate error message"
        (let [response (app (content-type (request :put "/user/facebook" (ch/generate-string {:email "john@stuff.com"
                                                                                              :first_name "john" :last_name "stuff"
                                                                                              :dob "05/10/1984"
                                                                                              :country_code "USA"
                                                                                              :img_url "http://image.com/image.jpg"
                                                                                              :topics ["Defence" "Arts"]})) "application/json"))]
          (:status response) => 400
          (:body response ) => (ch/generate-string {:errors [{:token "A token is required for social media registerations and logins"}]})))

  (fact "Create a new user, with an existing email, should return 409"
        (let [_ (app (content-type (request :put "/user" (ch/generate-string {:email "john@stuff.com" :password "stuff2"
                                                                              :first_name "john" :last_name "stuff"
                                                                              :dob "05/10/1984"
                                                                              :country_code "USA"
                                                                              :topics ["Defence" "Arts"]})) "application/json"))
              response (app (content-type (request :put "/user" (ch/generate-string {:email "john@stuff.com" :password "stuff2"
                                                                                     :first_name "john" :last_name "stuff"
                                                                                     :dob "05/10/1984"
                                                                                     :country_code "USA"
                                                                                     :topics ["Defence" "Arts"]})) "application/json"))]
          (:status response) => 409
          (:body response ) => (ch/generate-string existing-user-error-msg)))

  (fact "Create a new facebook user, with an existing email, should return 409"
        (let [_ (app (content-type (request :put "/user/facebook" (ch/generate-string {:email "john@stuff.com"
                                                                              :first_name "john" :last_name "stuff"
                                                                              :dob "05/10/1984"
                                                                              :country_code "USA"
                                                                              :img_url "http://image.com/image.jpg"
                                                                              :topics ["Defence" "Arts"]
                                                                              :token "token"})) "application/json"))
              response (app (content-type (request :put "/user/facebook" (ch/generate-string {:email "john@stuff.com"
                                                                                     :first_name "john" :last_name "stuff"
                                                                                     :dob "05/10/1984"
                                                                                     :country_code "USA"
                                                                                     :img_url "http://image.com/image.jpg"
                                                                                     :topics ["Defence" "Arts"]
                                                                                     :token "token"})) "application/json"))]
          (:status response) => 409
          (:body response ) => (ch/generate-string existing-user-error-msg)))

  (fact "Create a new user, when the payload is missing an email, return 400 with appropriate error message"
        (let [response (app (content-type (request :put "/user" (ch/generate-string {:password "stuff2"
                                                                                     :first_name "john" :last_name "stuff"
                                                                                     :dob "05/10/1984"
                                                                                     :country_code "USA"
                                                                                     :topics ["Defence" "Arts"]})) "application/json"))]
          (:status response) => 400
          (:body response ) => (ch/generate-string {:errors [{:email "A valid email address is a required"}]})))

  (fact "Create a new user, when the payload is missing a password, return 400 with appropriate error message"
        (let [response (app (content-type (request :put "/user" (ch/generate-string {:email "john@stuff.com"
                                                                                     :first_name "john" :last_name "stuff"
                                                                                     :dob "05/10/1984"
                                                                                     :country_code "USA"
                                                                                     :topics ["Defence" "Arts"]})) "application/json"))]
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
                                                              {:dob "Date of birth is a required field"}
                                                              {:country_code "Country Code is a required field.  Please Specify Country Code"
                                                               :topics "Please specify a list of topics."}]}) :in-any-order)))

  (fact "Create a new user, when the email is invalid, return 400 with appropriate error message"
    (let [response (app (content-type (request :put "/user" (ch/generate-string {:email "johnstuffcom" :password "stuff2"
                                                                                 :first_name "john" :last_name "stuff"
                                                                                 :dob "05/10/1984"
                                                                                 :country_code "USA"
                                                                                 :topics ["Defence" "Arts"]})) "application/json"))]
      (:status response) => 400
      (:body response ) => (contains (ch/generate-string {:errors [{:email "A valid email address is a required"}]}) :in-any-order)))

  (fact "Create a new user, when the country is invalid, return 400 with appropriate error message"
        (let [response (app (content-type (request :put "/user" (ch/generate-string {:email "john@stuff.com" :password "stuff2"
                                                                                     :first_name "john" :last_name "stuff"
                                                                                     :dob "05/10/1984"
                                                                                     :country_code ""
                                                                                     :topics ["Defence" "Arts"]})) "application/json"))]
          (:status response) => 400
          (:body response ) => (contains (ch/generate-string {:errors [{:country_code "Country Code is a required field.  Please Specify Country Code"}]}) :in-any-order)))


  (fact "Create a new user, when the country code is invalid, return 400 with appropriate error message"
        (let [response (app (content-type (request :put "/user" (ch/generate-string {:email "john@stuff.com" :password "stuff2"
                                                                                     :first_name "john" :last_name "stuff"
                                                                                     :dob "05/10/1984"
                                                                                     :country_code "UPA"
                                                                                     :topics ["Defence" "Arts"]})) "application/json"))]
          (:status response) => 400
          (:body response ) => (contains (ch/generate-string {:errors [{:country_code "Country Code is a required field.  Please Specify Country Code"}]}) :in-any-order)))

  (fact "Create a new user, when the password is invalid, return 400 with appropriate error message"
      (let [response (app (content-type (request :put "/user" (ch/generate-string {:email "john@stuff.com" :password ""
                                                                                   :first_name "john" :last_name "stuff"
                                                                                   :dob "05/10/1984"
                                                                                   :country_code "USA"
                                                                                   :topics ["Defence" "Arts"]})) "application/json"))]
        (:status response) => 400
        (:body response ) => (contains (ch/generate-string {:errors [{:password "Password is a required field"}]}) :in-any-order)))

  (fact "Retrieve a user by email"
         (let [{:keys [token]} (ch/parse-string (:body (app (content-type (request :put "/user" (ch/generate-string {:email "john@stuff.com" :password "stuff2"
                                                                                                                     :first_name "john" :last_name "stuff"
                                                                                                                     :dob "05/10/1984"
                                                                                                                     :country_code "USA"
                                                                                                                     :topics ["Defence" "Arts"]})) "application/json"))) true)
               response (app (header (request :get "/user/john@stuff.com")"Authorization" (str "PAV_AUTH_TOKEN " token)))]
           (:status response) => 200
           (ch/parse-string (:body response) true) => (contains {:email "john@stuff.com"
                                                                 :first_name "john" :last_name "stuff"
                                                                 :dob "05/10/1984"
                                                                 :country_code "USA"
                                                                 :topics ["Defence" "Arts"]} :in-any-order)))

  (fact "Retrieve a user by email that doesn't exist"
        (let [{:keys [token]} (ch/parse-string (:body (app (content-type (request :put "/user" (ch/generate-string {:email "john@stuff.com" :password "stuff2"
                                                                                                                    :first_name "john" :last_name "stuff"
                                                                                                                    :dob "05/10/1984"
                                                                                                                    :country_code "USA"
                                                                                                                    :topics ["Defence" "Arts"]})) "application/json"))) true)
              response (app (header (request :get "/user/peter@stuff.com") "Authorization" (str "PAV_AUTH_TOKEN " token)))]
          (:status response) => 200
          (:body response) => ""))

  (fact "Retrieve a user by email, without authentication token"
        (let [response (app (request :get "/user/johnny@stuff.com"))]
          (:status response) => 401))

  (fact "Create token for user when logging on"
        (let [_ (app (content-type (request :put "/user" (ch/generate-string {:email "john@stuff.com" :password "stuff2"
                                                                              :first_name "john" :last_name "stuff"
                                                                              :dob "05/10/1984"
                                                                              :country_code "USA"
                                                                              :topics ["Defence" "Arts"]})) "application/json"))
              login-response (app (content-type (request :post "/user/authenticate" (ch/generate-string {:email "john@stuff.com" :password "stuff2"})) "application/json"))]
          (:status login-response) => 201
          (keys (ch/parse-string (:body login-response) true)) => (contains [:token])))

  (fact "Create token for facebook user when logging on"
        (let [_ (app (content-type (request :put "/user/facebook" (ch/generate-string {:email "paul@facebook.com"
                                                                                       :first_name "john" :last_name "stuff"
                                                                                       :dob "05/10/1984"
                                                                                       :country_code "USA"
                                                                                       :img_url "http://image.com/image.jpg"
                                                                                       :topics ["Defence" "Arts"]
                                                                                       :token "token"})) "application/json"))
              login-response (app (content-type (request :post "/user/facebook/authenticate" (ch/generate-string {:email "paul@facebook.com" :token "token"})) "application/json"))]
          (:status login-response) => 201
          (keys (ch/parse-string (:body login-response) true)) => (contains [:token])))

  (fact "Create token for user that doesn't exist, returns 401 with suitable error message"
        (let [_ (app (content-type (request :put "/user" (ch/generate-string {:email "john@stuff.com" :password "stuff2"
                                                                              :first_name "john" :last_name "stuff"
                                                                              :dob "05/10/1984"
                                                                              :country_code "USA"
                                                                              :topics ["Defence" "Arts"]})) "application/json"))
              login-response (app (content-type (request :post "/user/authenticate" (ch/generate-string {:email "john@stuff.com" :password "invalid"})) "application/json"))]
          (:status login-response) => 401
          (:body login-response) => login-error-msg))

  (fact "Create token for user, when payload doesn't contain an email then returns 400 with suitable error message"
        (let [_ (app (content-type (request :put "/user" (ch/generate-string {:email "john@stuff.com" :password "stuff2"
                                                                              :first_name "john" :last_name "stuff"
                                                                              :dob "05/10/1984"
                                                                              :country_code "USA"
                                                                              :topics ["Defence" "Arts"]})) "application/json"))
              login-response (app (content-type (request :post "/user/authenticate" (ch/generate-string {:password "stuff2"})) "application/json"))]
          (:status login-response) => 400
          (:body login-response) => (ch/generate-string {:errors [{:email "A valid email address is a required"}]}))))

