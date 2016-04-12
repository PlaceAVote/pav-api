(ns com.pav.api.test.user.user-test
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
                                      (flush-redis)
                                      (flush-es-indexes)
                                      (bootstrap-bills-and-metadata)))]

   (fact "Create a new user, will return 201 status and newly created token"
         (let [{status :status body :body} (pav-req :put "/user" test-user)]
           status => 201
           (keys (ch/parse-string body true)) => (contains [:user_id :token] :in-any-order)))

  (fact "Create new user, When valid zip+4 is provided, Then create new user"
    (let [{status :status body :body} (pav-req :put "/user" (assoc test-user :zipcode "77068"))]
      status => 201
      (keys (ch/parse-string body true)) => (contains [:user_id :token] :in-any-order)))

  (fact "Create new user, When zip+4 contains only 2 characters, Then return 400 exception"
    (let [{status :status body :body} (pav-req :put "/user" (assoc test-user :zipcode "77"))
          response (ch/parse-string body true)]
      status => 400
      response => {:errors [{:zipcode "A valid 5 digit zipcode code is required for US citizens, e.g 90210"}]}))

  (fact "Create new user, When zip+4 code doesn't correspond to a state and district, Then return 400 exception"
    (let [{status :status body :body} (pav-req :put "/user" (assoc test-user :zipcode "00045"))
          response (ch/parse-string body true)]
      status => 400
      response => {:errors [{:zipcode "A valid 5 digit zipcode code is required for US citizens, e.g 90210"}]}))

   (fact "Create a new user from facebook login, will return 201 status and newly created user profile"
         (let [{status :status body :body} (pav-req :put "/user/facebook" test-fb-user)]
           status => 201
           (keys (ch/parse-string body true)) => (contains [:user_id :token] :in-any-order)))

  (fact "Create a new user, with an existing email, should return 409"
        (let [_ (pav-req :put "/user" test-user)
              {status :status body :body} (pav-req :put "/user" test-user)]
          status => 409
          body => (ch/generate-string existing-user-error-msg)))

  (fact "Create a new facebook user, with an existing email, should return 409"
        (let [_ (pav-req :put "/user/facebook" test-fb-user)
              {status :status body :body} (pav-req :put "/user/facebook" test-fb-user)]
          status => 409
          body => (ch/generate-string existing-user-error-msg)))

  (fact "Create a new user, when the payload is empty, return 400 with appropriate error messages"
		(let [{status :status body :body} (pav-req :put "/user" {})]
			status => 400
			body => (contains (ch/generate-string {:errors [{:email "A valid email address is a required"}
																											{:password "Password must be a minimum of 6 characters in length."}
																											{:first_name "First Name is a required field"}
																											{:last_name "Last Name is a required field"}
																											{:dob "Date of birth is a required field"}
																											{:topics "Please specify a list of topics."}
																											{:gender "Please specify a valid gender.  Valid values are male, female and they"}
                                                      {:zipcode "A valid 5 digit zipcode code is required for US citizens, e.g 90210"}]}) :in-any-order)))

	(fact "Create a new facebook user, when the payload is empty, return 400 with appropriate error messages"
		(let [{status :status body :body} (pav-req :put "/user/facebook" {})]
			status => 400
			body => (contains (ch/generate-string {:errors [{:email "A valid email address is a required"}
																											{:first_name "First Name is a required field"}
																											{:last_name "Last Name is a required field"}
																											{:img_url "A IMG URL is required for social media registerations and logins"}
																											{:dob "Date of birth is a required field"}
																											{:topics "Please specify a list of topics."}
																											{:token "A token is required for social media registerations and logins"}
																											{:gender "Please specify a valid gender.  Valid values are male, female and they"}
																											{:id "Please specify a facebook id"}
                                                      {:zipcode "A valid 5 digit zipcode code is required for US citizens, e.g 90210"}]}) :in-any-order)))

  (fact "Create a new user, when the email is invalid, return 400 with appropriate error message"
    (let [{status :status body :body} (pav-req :put "/user" (assoc test-user :email "johnstuffcom"))]
      status => 400
      body => (contains (ch/generate-string {:errors [{:email "A valid email address is a required"}]}) :in-any-order)))

  (fact "Create a new user, when the country is invalid, return 400 with appropriate error message"
        (let [{status :status body :body} (pav-req :put "/user" (assoc test-user :country_code ""))]
          status => 400
          body => (contains (ch/generate-string {:errors [{:country_code "Country Code is a required field.  Please Specify Country Code"}]}) :in-any-order)))

  (fact "Create a new user, when the country code is invalid, return 400 with appropriate error message"
        (let [{status :status body :body} (pav-req :put "/user" (assoc test-user :country_code "UPA"))]
          status => 400
          body => (contains (ch/generate-string {:errors [{:country_code "Country Code is a required field.  Please Specify Country Code"}]}) :in-any-order)))

  (fact "Create a new user, when the password is invalid, return 400 with appropriate error message"
      (let [{status :status body :body} (pav-req :put "/user" (assoc test-user :password ""))]
        status => 400
        body => (contains (ch/generate-string {:errors [{:password "Password must be a minimum of 6 characters in length."}]}) :in-any-order)))

  (fact "Create a new user, When password is less than 6 characters, Then return 400 with appropriate error message"
    (let [{status :status body :body} (pav-req :put "/user" (assoc test-user :password "passw"))
          body (ch/parse-string body true)]
      status => 400
      body => (contains {:errors [{:password "Password must be a minimum of 6 characters in length."}]} :in-any-order)))

  (fact "Create token for user when logging on"
        (let [_ (pav-req :put "/user" test-user)
              {status :status body :body} (pav-req :post "/user/authenticate" {:email "john@stuff.com" :password "stuff2"})]
          status => 201
          (keys (ch/parse-string body true)) => (contains [:token])))

  (fact "Create token for facebook user when logging on"
        (let [_ (pav-req :put "/user/facebook" test-fb-user)
              {status :status body :body} (pav-req :post "/user/facebook/authenticate" (select-keys test-fb-user [:id :token :email]))]
          status => 201
          (keys (ch/parse-string body true)) => (contains [:token])))

	(fact "(Migration) When user with facebook id doesn't exist, use email address to reauthenticate the user."
		(let [_ (pav-req :put "/user/facebook" test-fb-user)
					{first_login_attempt :status} (pav-req :post "/user/facebook/authenticate" (merge {:id "100101"} (select-keys test-fb-user [:token :email])))
					{second_login_attempt :status body :body} (pav-req :post "/user/facebook/authenticate" (merge {:id "100101"} (select-keys test-fb-user [:token :email])))]
			first_login_attempt => 201
			second_login_attempt => 201
			(keys (ch/parse-string body true)) => (contains [:token])))

  (fact "Create token for user that doesn't exist, returns 401 with suitable error message"
        (let [_ (pav-req :put "/user" test-user)
              {status :status body :body} (pav-req :post "/user/authenticate" {:email "john@stuff.com" :password "invalid"})
              body (ch/parse-string body true)]
          status => 401
          body => {:error "Invalid Login credientials"}))

  (fact "Create token for user, when authentication payload doesn't contain an email then returns 400 with suitable error message"
        (let [_ (pav-req :put "/user" test-user)
              {status :status body :body} (pav-req :post "/user/authenticate" {:password "stuff2"})
              body (ch/parse-string body true)]
          status => 400
          body => {:errors [{:email "A valid email address is a required"}]}))

  (fact "Given confirmation token, when invalid, then return 401."
        (let [{status :status} (pav-req :post "/user/confirm/1234")]
          status => 401))

  (fact "Create a new user, When password contains numbers and special characters, Then accept password and return 201"
    (let [{status :status} (pav-req :put "/user" (assoc test-user :password "password123!@#$^*"))]
      status => 201))

  (fact "Create a new user, When password contains 6 character spaces, Then reject password and return 400"
    (let [{status :status} (pav-req :put "/user" (assoc test-user :password "      "))]
      status => 400))

  (fact "Create a new user, When password contains whitespace at the start and end, Then reject password and return 400"
    (let [{status :status} (pav-req :put "/user" (assoc test-user :password " password2! "))]
      status => 400))
	)