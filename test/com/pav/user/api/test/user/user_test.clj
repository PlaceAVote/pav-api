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

(defn pav-req
  ([method url] (app (content-type (request method url) "application/json")))
  ([method url payload] (app (content-type (request method url (ch/generate-string payload)) "application/json")))
  ([method url token payload] (app (content-type (header (request method url (ch/generate-string payload))
                                                         "Authorization" (str "PAV_AUTH_TOKEN " token)) "application/json"))))

(against-background [(before :facts (do
                                      (delete-user-table)
                                      (create-user-table)))]

   (fact "Create a new user, will return 201 status and newly created user"
         (let [{status :status body :body} (pav-req :put "/user"
                                              {:email "john@stuff.com"
                                               :password "stuff2"
                                               :first_name "john"
                                               :last_name "stuff"
                                               :dob "05/10/1984"
                                               :country_code "USA"
                                               :topics ["Defence" "Arts"]})]
           status => 201
           (keys (ch/parse-string body true)) => (contains [:user_id :token :email :first_name :last_name :dob :country_code
                                                            :topics :created_at :registered] :in-any-order)))

   (fact "Create a new user from facebook login, will return 201 status and newly created user profile"
         (let [{status :status body :body} (pav-req :put "/user/facebook"
                                               {:email "paul@facebook.com"
                                                :first_name "john" :last_name "stuff"
                                                :dob "05/10/1984"
                                                :country_code "USA"
                                                :img_url "http://image.com/image.jpg"
                                                :topics ["Defence" "Arts"]
                                                :token "token"})]
           status => 201
           (keys (ch/parse-string body true)) => (contains [:user_id :email :first_name :last_name :dob :country_code
                                                            :img_url :topics :token :created_at :registered] :in-any-order)))


  (fact "Create a new user from facebook login, when email is missing, return 400 with appropriate error message"
        (let [{status :status body :body} (pav-req :put "/user/facebook"
                                                   {:first_name "john"
                                                    :last_name "stuff"
                                                    :dob "05/10/1984"
                                                    :country_code "USA"
                                                    :img_url "http://image.com/image.jpg"
                                                    :topics ["Defence" "Arts"]
                                                    :token "token"})]
          status => 400
          body => (ch/generate-string {:errors [{:email "A valid email address is a required"}]})))

  (fact "Create a new user from facebook login, when token is missing, return 400 with appropriate error message"
        (let [{status :status body :body} (pav-req :put "/user/facebook"
                                                   {:email "john@stuff.com"
                                                    :first_name "john"
                                                    :last_name "stuff"
                                                    :dob "05/10/1984"
                                                    :country_code "USA"
                                                    :img_url "http://image.com/image.jpg"
                                                    :topics ["Defence" "Arts"]})]
          status => 400
          body => (ch/generate-string {:errors [{:token "A token is required for social media registerations and logins"}]})))

  (fact "Create a new user, with an existing email, should return 409"
        (let [_ (pav-req :put "/user"
                         {:email "john@stuff.com"
                          :password "stuff2"
                          :first_name "john"
                          :last_name "stuff"
                          :dob "05/10/1984"
                          :country_code "USA"
                          :topics ["Defence" "Arts"]})
              {status :status body :body} (pav-req :put "/user"
                                                   {:email "john@stuff.com"
                                                    :password "stuff2"
                                                    :first_name "john"
                                                    :last_name "stuff"
                                                    :dob "05/10/1984"
                                                    :country_code "USA"
                                                    :topics ["Defence" "Arts"]})]
          status => 409
          body => (ch/generate-string existing-user-error-msg)))

  (fact "Create a new facebook user, with an existing email, should return 409"
        (let [_ (pav-req :put "/user/facebook" {:email "john@stuff.com"
                                                :first_name "john" :last_name "stuff"
                                                :dob "05/10/1984"
                                                :country_code "USA"
                                                :img_url "http://image.com/image.jpg"
                                                :topics ["Defence" "Arts"]
                                                :token "token"})
              {status :status body :body} (pav-req :put "/user/facebook"
                                                   {:email "john@stuff.com"
                                                    :first_name "john"
                                                    :last_name "stuff"
                                                    :dob "05/10/1984"
                                                    :country_code "USA"
                                                    :img_url "http://image.com/image.jpg"
                                                    :topics ["Defence" "Arts"]
                                                    :token "token"})]
          status => 409
          body => (ch/generate-string existing-user-error-msg)))

  (fact "Create a new user, when the payload is missing an email, return 400 with appropriate error message"
        (let [{status :status body :body} (pav-req :put "/user"
                                                   {:password "stuff2"
                                                    :first_name "john" :last_name "stuff"
                                                    :dob "05/10/1984"
                                                    :country_code "USA"
                                                    :topics ["Defence" "Arts"]})]
          status => 400
          body => (ch/generate-string {:errors [{:email "A valid email address is a required"}]})))

  (fact "Create a new user, when the payload is missing a password, return 400 with appropriate error message"
        (let [{status :status body :body} (pav-req :put "/user" {:email "john@stuff.com"
                                                                 :first_name "john" :last_name "stuff"
                                                                 :dob "05/10/1984"
                                                                 :country_code "USA"
                                                                 :topics ["Defence" "Arts"]})]
          status => 400
          body => (ch/generate-string {:errors [{:password "Password is a required field"}]})))

  (fact "Create a new user, when the payload is empty, return 400 with appropriate error message"
        (let [{status :status body :body} (pav-req :put "/user" {})]
          status => 400
          body => (contains (ch/generate-string {:errors [{:email "A valid email address is a required"}
                                                          {:password "Password is a required field"}
                                                          {:first_name "First Name is a required field"}
                                                          {:last_name "Last Name is a required field"}
                                                          {:dob "Date of birth is a required field"}
                                                          {:country_code "Country Code is a required field.  Please Specify Country Code"
                                                           :topics "Please specify a list of topics."}]}) :in-any-order)))

  (fact "Create a new user, when the email is invalid, return 400 with appropriate error message"
    (let [{status :status body :body} (pav-req :put "/user" {:email "johnstuffcom"
                                                             :password "stuff2"
                                                             :first_name "john"
                                                             :last_name "stuff"
                                                             :dob "05/10/1984"
                                                             :country_code "USA"
                                                             :topics ["Defence" "Arts"]})]
      status => 400
      body => (contains (ch/generate-string {:errors [{:email "A valid email address is a required"}]}) :in-any-order)))

  (fact "Create a new user, when the country is invalid, return 400 with appropriate error message"
        (let [{status :status body :body} (pav-req :put "/user" {:email "john@stuff.com"
                                                                 :password "stuff2"
                                                                 :first_name "john"
                                                                 :last_name "stuff"
                                                                 :dob "05/10/1984"
                                                                 :country_code ""
                                                                 :topics ["Defence" "Arts"]})]
          status => 400
          body => (contains (ch/generate-string {:errors [{:country_code "Country Code is a required field.  Please Specify Country Code"}]}) :in-any-order)))

  (fact "Create a new user, when the country code is invalid, return 400 with appropriate error message"
        (let [{status :status body :body} (pav-req :put "/user" {:email "john@stuff.com"
                                                                 :password "stuff2"
                                                                 :first_name "john"
                                                                 :last_name "stuff"
                                                                 :dob "05/10/1984"
                                                                 :country_code "UPA"
                                                                 :topics ["Defence" "Arts"]})]
          status => 400
          body => (contains (ch/generate-string {:errors [{:country_code "Country Code is a required field.  Please Specify Country Code"}]}) :in-any-order)))

  (fact "Create a new user, when the password is invalid, return 400 with appropriate error message"
      (let [{status :status body :body} (pav-req :put "/user" {:email "john@stuff.com"
                                                               :password ""
                                                               :first_name "john"
                                                               :last_name "stuff"
                                                               :dob "05/10/1984"
                                                               :country_code "USA"
                                                               :topics ["Defence" "Arts"]})]
        status => 400
        body => (contains (ch/generate-string {:errors [{:password "Password is a required field"}]}) :in-any-order)))

  (fact "Retrieve a user profile by authentication token"
         (let [{:keys [token]} (ch/parse-string (:body (pav-req :put "/user" {:email "john@stuff.com"
                                                                              :password "stuff2"
                                                                              :first_name "john" :last_name "stuff"
                                                                              :dob "05/10/1984"
                                                                              :country_code "USA"
                                                                              :topics ["Defence" "Arts"]})) true)
               {status :status body :body} (pav-req :get "/user" token {})]
           status => 200
           (ch/parse-string body true) => (contains {:email "john@stuff.com"
                                                     :first_name "john"
                                                     :last_name "stuff"
                                                     :dob "05/10/1984"
                                                     :country_code "USA"
                                                     :topics ["Defence" "Arts"]} :in-any-order)))

  (fact "Retrieve a user by email, without authentication token"
        (let [{status :status} (pav-req :get "/user")]
          status => 401))

  (fact "Create token for user when logging on"
        (let [_ (pav-req :put "/user" {:email "john@stuff.com"
                                       :password "stuff2"
                                       :first_name "john"
                                       :last_name "stuff"
                                       :dob "05/10/1984"
                                       :country_code "USA"
                                       :topics ["Defence" "Arts"]})
              {status :status body :body} (pav-req :post "/user/authenticate" {:email "john@stuff.com" :password "stuff2"})]
          status => 201
          (keys (ch/parse-string body true)) => (contains [:token])))

  (fact "Create token for facebook user when logging on"
        (let [_ (pav-req :put "/user/facebook" {:email "paul@facebook.com"
                                                :first_name "john"
                                                :last_name "stuff"
                                                :dob "05/10/1984"
                                                :country_code "USA"
                                                :img_url "http://image.com/image.jpg"
                                                :topics ["Defence" "Arts"]
                                                :token "token"})
              {status :status body :body} (pav-req :post "/user/facebook/authenticate" {:email "paul@facebook.com" :token "token"})]
          status => 201
          (keys (ch/parse-string body true)) => (contains [:token])))

  (fact "Create token for user that doesn't exist, returns 401 with suitable error message"
        (let [_ (pav-req :put "/user" {:email "john@stuff.com"
                                       :password "stuff2"
                                       :first_name "john"
                                       :last_name "stuff"
                                       :dob "05/10/1984"
                                       :country_code "USA"
                                       :topics ["Defence" "Arts"]})
              {status :status body :body} (pav-req :post "/user/authenticate" {:email "john@stuff.com" :password "invalid"})]
          status => 401
          body => login-error-msg))

  (fact "Create token for user, when payload doesn't contain an email then returns 400 with suitable error message"
        (let [_ (pav-req :put "/user" {:email "john@stuff.com"
                                       :password "stuff2"
                                       :first_name "john" :last_name "stuff"
                                       :dob "05/10/1984"
                                       :country_code "USA"
                                       :topics ["Defence" "Arts"]})
              {status :status body :body} (pav-req :post "/user/authenticate" {:password "stuff2"})]
          status => 400
          body => (ch/generate-string {:errors [{:email "A valid email address is a required"}]})))

  (fact "Given confirmation token, when invalid, then return 401."
        (let [{status :status} (pav-req :post "/user/confirm/1234")]
          status => 401))

  (fact "Retrieve users notifications"
        (let [{body :body} (pav-req :put "/user" {:email "john@pl.com"
                                       :password "stuff2"
                                       :first_name "john" :last_name "stuff"
                                       :dob "05/10/1984"
                                       :country_code "USA"
                                       :topics ["Defence" "Arts"]})
              {token :token} (ch/parse-string body true)
              {status :status} (pav-req :get "/user/notifications" token {})]
          status => 200))

  (fact "Retrieving user notifications without Authentication token, results in 401"
        (let [{status :status} (pav-req :get "/user/notifications" "token" {})]
          status => 401))
  )

