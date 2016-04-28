(ns com.pav.api.test.user.user-test
  (:use midje.sweet)
  (:require [com.pav.api.test.utils.utils :refer [flush-dynamo-tables
                                                  flush-redis
                                                  flush-es-indexes
                                                  bootstrap-bills-and-metadata
                                                  pav-req
                                                  new-pav-user
                                                  new-fb-user]]
            [com.pav.api.resources.user :refer [existing-user-error-msg login-error-msg]]
            [com.pav.api.db.db :refer [empty-all-tables-unsafe!]]))

(against-background [(before :contents (do (flush-dynamo-tables)
                                           (empty-all-tables-unsafe!)
                                           (flush-redis)
                                           (flush-es-indexes)
                                           (bootstrap-bills-and-metadata)))]

  (fact "Create a new user, will return 201 status and newly created token"
    (let [{status :status body :body} (pav-req :put "/user" (new-pav-user))]
      status => 201
      (keys body) => (contains [:user_id :token] :in-any-order)))

  (fact "Create new user, When zip+4 contains only 2 characters, Then return 400 exception"
    (let [{status :status body :body} (pav-req :put "/user" (new-pav-user {:zipcode "77"}))]
      status => 400
      body => {:errors [{:zipcode "A valid 5 digit zipcode code is required for US citizens, e.g 90210"}]}))

  (fact "Create new user, When zip+4 code doesn't correspond to a state and district, Then return 400 exception"
    (let [{status :status body :body} (pav-req :put "/user" (new-pav-user {:zipcode "00045"}))]
      status => 400
      body => {:errors [{:zipcode "A valid 5 digit zipcode code is required for US citizens, e.g 90210"}]}))
;
;   (fact "Create a new user from facebook login, will return 201 status and newly created user profile"
;         (let [{status :status body :body} (pav-req :put "/user/facebook" (new-fb-user))]
;           status => 201
;           (keys body) => (contains [:user_id :token] :in-any-order)))
;
;  (fact "Create a new user, with an existing email, should return 409"
;        (let [{:keys [email] :as user} (new-pav-user)
;              _ (pav-req :put "/user" user)
;              {status :status body :body} (pav-req :put "/user" (new-pav-user {:email email}))]
;          status => 409
;          body => existing-user-error-msg))
;
;  (fact "Create a new facebook user, with an existing email, should return 409"
;        (let [{:keys [email] :as user} (new-fb-user)
;              _ (pav-req :put "/user/facebook" user)
;              {status :status body :body} (pav-req :put "/user/facebook" (new-fb-user {:email email}))]
;          status => 409
;          body => existing-user-error-msg))
;
;  (fact "Create a new user, when the payload is empty, return 400 with appropriate error messages"
;		(let [{status :status body :body} (pav-req :put "/user" {})]
;			status => 400
;			body => (contains {:errors [{:email "A valid email address is a required"}
;                                  {:password "Password must be a minimum of 6 characters in length."}
;                                  {:first_name "First Name is a required field"}
;                                  {:last_name "Last Name is a required field"}
;                                  {:dob "Date of birth is a required field"}
;                                  {:topics "Please specify a list of topics."}
;                                  {:gender "Please specify a valid gender.  Valid values are male, female and they"}
;                                  {:zipcode "A valid 5 digit zipcode code is required for US citizens, e.g 90210"}]} :in-any-order)))
;
;  (future-fact "Create a new facebook user, when the payload is empty, return 400 with appropriate error messages"
;		(let [{status :status body :body} (pav-req :put "/user/facebook" {})]
;			status => 400
;			body => (contains {:errors [{:email "A valid email address is a required"}
;                                  {:first_name "First Name is a required field"}
;                                  {:last_name "Last Name is a required field"}
;                                  {:img_url "A IMG URL is required for social media registerations and logins"}
;                                  {:dob "Date of birth is a required field"}
;                                  {:topics "Please specify a list of topics."}
;                                  {:token "A token is required for social media registerations and logins"}
;                                  {:gender "Please specify a valid gender.  Valid values are male, female and they"}
;                                  {:id "Please specify a facebook id"}
;                                  {:zipcode "A valid 5 digit zipcode code is required for US citizens, e.g 90210"}]}) :in-any-order))
;
;  (fact "Create a new user, when the email is invalid, return 400 with appropriate error message"
;    (let [{status :status body :body} (pav-req :put "/user" (new-pav-user {:email "johnstuffcom"}))]
;      status => 400
;      body => {:errors [{:email "A valid email address is a required"}]}))
;
;  (fact "Create a new user, when the password is invalid, return 400 with appropriate error message"
;      (let [{status :status body :body} (pav-req :put "/user" (new-pav-user {:password ""}))]
;        status => 400
;        body => {:errors [{:password "Password must be a minimum of 6 characters in length."}]}))
;
;  (fact "Create a new user, When password is less than 6 characters, Then return 400 with appropriate error message"
;    (let [{status :status body :body} (pav-req :put "/user" (new-pav-user {:password "passw"}))]
;      status => 400
;      body => {:errors [{:password "Password must be a minimum of 6 characters in length."}]}))
;
;  (fact "Create token for user when logging on"
;    (let [{:keys [email password] :as user} (new-pav-user)
;          _ (pav-req :put "/user" user)
;          {status :status body :body} (pav-req :post "/user/authenticate" {:email email :password password})]
;      status => 201
;      (keys body) => (contains [:token])))
;
;  (fact "Create token for facebook user when logging on"
;    (let [{:keys [email id token] :as user} (new-fb-user)
;          _ (pav-req :put "/user/facebook" user)
;          {status :status body :body} (pav-req :post "/user/facebook/authenticate" {:email email :id id :token token})]
;      status => 201
;      (keys body) => (contains [:token])))
;
;  (fact "Create token for user that doesn't exist, returns 401 with suitable error message"
;    (let [{:keys [email] :as user} (new-fb-user)
;          _ (pav-req :put "/user/facebook" user)
;          {status :status body :body} (pav-req :post "/user/authenticate" {:email email :password "invalid"})]
;      status => 401
;      body => {:error "Invalid Login credientials"}))
;
;  (fact "Create token for user, when authentication payload doesn't contain an email then returns 400 with suitable error message"
;    (let [{status :status body :body} (pav-req :post "/user/authenticate" {:password "stuff2"})]
;      status => 400
;      body => {:errors [{:email "A valid email address is a required"}]}))
;
;  (fact "Given confirmation token, when invalid, then return 401."
;    (let [{status :status} (pav-req :post "/user/confirm/1234")]
;      status => 401))
;
;  (fact "Create a new user, When password contains numbers and special characters, Then accept password and return 201"
;    (let [{status :status} (pav-req :put "/user" (new-pav-user {:password "password123!@#$^*"}))]
;      status => 201))
;
;  (fact "Create a new user, When password contains 6 character spaces, Then reject password and return 400"
;    (let [{status :status} (pav-req :put "/user" (new-pav-user {:password "      "}))]
;      status => 400))
;
;  (fact "Create a new user, When password contains whitespace at the start and end, Then reject password and return 400"
;    (let [{status :status} (pav-req :put "/user" (new-pav-user {:password " password2! "}))]
;      status => 400))
)
