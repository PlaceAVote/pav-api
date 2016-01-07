(ns com.pav.user.api.test.user.user-test
  (:use midje.sweet)
  (:require [com.pav.user.api.handler :refer [app]]
            [com.pav.user.api.test.utils.utils :refer [make-request parse-response-body
																											 flush-dynamo-tables
                                                       flush-redis
                                                       persist-timeline-event
																											 persist-notification-event
                                                       flush-user-index
                                                       bootstrap-bills
																											 create-comment
																											 pav-req]]
            [ring.mock.request :refer [request body content-type header]]
            [com.pav.user.api.resources.user :refer [existing-user-error-msg login-error-msg]]
            [com.pav.user.api.authentication.authentication :refer [create-auth-token]]
            [cheshire.core :as ch]
            [com.pav.user.api.redis.redis :as redis-dao]))

(def test-user {:email "john@stuff.com" :password "stuff2" :first_name "john" :last_name "stuff" :dob "05/10/1984"
								:country_code "USA" :topics ["Defense"]})

(def test-fb-user {:email "paul@facebook.com" :first_name "john" :last_name "stuff" :dob "05/10/1984" :country_code "USA"
									 :img_url "http://image.com/image.jpg" :topics ["Defense"] :token "token"})

(against-background [(before :facts (do
                                      (flush-dynamo-tables)
                                      (flush-redis)
                                      (flush-user-index)
                                      (bootstrap-bills)))]

   (fact "Create a new user, will return 201 status and newly created user"
         (let [{status :status body :body} (pav-req :put "/user" test-user)]
           status => 201
           (keys (ch/parse-string body true)) => (contains [:user_id :token :email :first_name :last_name :dob :country_code
                                                            :topics :created_at :registered :public] :in-any-order)))

   (fact "Create a new user from facebook login, will return 201 status and newly created user profile"
         (let [{status :status body :body} (pav-req :put "/user/facebook" test-fb-user)]
           status => 201
           (keys (ch/parse-string body true)) => (contains [:user_id :email :first_name :last_name :dob :country_code
                                                            :img_url :topics :token :created_at :registered :public] :in-any-order)))


  (fact "Create a new user from facebook login, when email is missing, return 400 with appropriate error message"
        (let [{status :status body :body} (pav-req :put "/user/facebook" (dissoc test-fb-user :email))]
          status => 400
          body => (ch/generate-string {:errors [{:email "A valid email address is a required"}]})))

  (fact "Create a new user from facebook login, when token is missing, return 400 with appropriate error message"
        (let [{status :status body :body} (pav-req :put "/user/facebook" (dissoc test-fb-user :token))]
          status => 400
          body => (ch/generate-string {:errors [{:token "A token is required for social media registerations and logins"}]})))

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

  (fact "Create a new user, when the payload is missing an email, return 400 with appropriate error message"
        (let [{status :status body :body} (pav-req :put "/user" (dissoc test-user :email))]
          status => 400
          body => (ch/generate-string {:errors [{:email "A valid email address is a required"}]})))

  (fact "Create a new user, when the payload is missing a password, return 400 with appropriate error message"
        (let [{status :status body :body} (pav-req :put "/user" (dissoc test-user :password))]
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
        body => (contains (ch/generate-string {:errors [{:password "Password is a required field"}]}) :in-any-order)))

  (fact "Create token for user when logging on"
        (let [_ (pav-req :put "/user" test-user)
              {status :status body :body} (pav-req :post "/user/authenticate" {:email "john@stuff.com" :password "stuff2"})
              {retrieve-user-status :status user-profile :body} (pav-req :get "/user" (:token (ch/parse-string body true)) {})]
          status => 201
          retrieve-user-status => 200
          (keys (ch/parse-string user-profile true)) => (contains [:user_id])
          (keys (ch/parse-string body true)) => (contains [:token])))

  (fact "Create token for facebook user when logging on"
        (let [_ (pav-req :put "/user/facebook" test-fb-user)
              {status :status body :body} (pav-req :post "/user/facebook/authenticate" {:email "paul@facebook.com" :token "token"})
              {retrieve-user-status :status user-profile :body} (pav-req :get "/user" (:token (ch/parse-string body true)) {})]
          status => 201
          retrieve-user-status => 200
          (keys (ch/parse-string user-profile true)) => (contains [:user_id])
          (keys (ch/parse-string body true)) => (contains [:token])))

  (fact "Create token for user that doesn't exist, returns 401 with suitable error message"
        (let [_ (pav-req :put "/user" test-user)
              {status :status body :body} (pav-req :post "/user/authenticate" {:email "john@stuff.com" :password "invalid"})]
          status => 401
          body => login-error-msg))

  (fact "Create token for user, when authentication payload doesn't contain an email then returns 400 with suitable error message"
        (let [_ (pav-req :put "/user" test-user)
              {status :status body :body} (pav-req :post "/user/authenticate" {:password "stuff2"})]
          status => 400
          body => (ch/generate-string {:errors [{:email "A valid email address is a required"}]})))

  (fact "Given confirmation token, when invalid, then return 401."
        (let [{status :status} (pav-req :post "/user/confirm/1234")]
          status => 401))

  (fact "Verify the JWT Token"
        (let [{follower :body} (pav-req :put "/user" test-user)
              {token :token} (ch/parse-string follower true)
              {status :status} (pav-req :get (str "/user/token/validate?token=" token))]
          status => 200))

  (fact "Verify the JWT Token, when invalid, return 401."
       (let [{status :status} (pav-req :get "/user/token/validate?token=rubbish")]
        status => 401))

  (fact "Reset existing user password"
    (let [test-user {:email     "john@placeavote.com" :password "stuff2" :first_name "john"
                     :last_name "stuff" :dob "05/10/1984" :country_code "USA" :topics ["Defense"]}
          _ (pav-req :put "/user" test-user)
          _ (pav-req :post (str "/password/reset?email=" (:email test-user)))
          reset-token (redis-dao/retrieve-password-reset-token-by-useremail (:email test-user))
          _ (pav-req :post "/password/reset/confirm" {:new_password "password1" :reset_token reset-token})
          {status :status} (pav-req :post "/user/authenticate" {:email (:email test-user) :password "password1"})]
      status => 201))

	(fact "Try resetting password with invalid email, should return 401"
		(let [{status :status} (pav-req :post (str "/password/reset?email=rubbish@em.com"))]
			status => 401)))