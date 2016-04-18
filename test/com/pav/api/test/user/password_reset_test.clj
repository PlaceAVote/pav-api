(ns com.pav.api.test.user.password-reset-test
	(:use midje.sweet)
	(:require [com.pav.api.test.utils.utils :refer [flush-dynamo-tables
																									flush-redis
																									flush-es-indexes
																									bootstrap-bills-and-metadata
																									pav-req
                                                  new-pav-user
                                                  new-fb-user]]
						[com.pav.api.redis.redis :as redis-dao]))

(against-background [(before :contents (do
                                         (flush-redis)
																			   (flush-dynamo-tables)
																			   (flush-es-indexes)
                                         (bootstrap-bills-and-metadata)))]

	(fact "Reset existing user password"
		(let [{:keys [email] :as user} (new-pav-user)
          _ (pav-req :put "/user" user)
					_ (pav-req :post (str "/password/reset?email=" email))
					reset-token (redis-dao/retrieve-password-reset-token-by-useremail email)
					_ (pav-req :post "/password/reset/confirm" {:new_password "password1" :reset_token reset-token})
					{status :status} (pav-req :post "/user/authenticate" {:email email :password "password1"})]
			status => 201))

  (fact "Reset existing user password, When email contains Capital letters, Then treat it as case insensitve."
    (let [test-email (clojure.string/upper-case "john@placeavote.com")
          _ (pav-req :put "/user" (new-pav-user {:email test-email}))
          _ (pav-req :post (str "/password/reset?email=" test-email))
          reset-token (redis-dao/retrieve-password-reset-token-by-useremail "john@placeavote.com")
          _ (pav-req :post "/password/reset/confirm" {:new_password "password1" :reset_token reset-token})
          {status :status} (pav-req :post "/user/authenticate" {:email test-email :password "password1"})]
      status => 201))

	(fact "Reset existing user password, When given an invalid reset_token, Then return 401"
		(let [{:keys [email] :as user} (new-pav-user)
          _ (pav-req :put "/user" user)
					_ (pav-req :post (str "/password/reset?email=" email))
					{status :status} (pav-req :post "/password/reset/confirm" {:new_password "password1" :reset_token "invalidtoken"})]
			status => 401))

	(fact "Try resetting password with invalid email, should return 401"
		(let [{status :status} (pav-req :post (str "/password/reset?email=rubbish@em.com"))]
			status => 401))

	(fact "Try resetting password, When payload is empty, Then return 400 response with correct error payload"
		(let [{status :status body :body} (pav-req :post "/password/reset/confirm" {})]
			status => 400
			body => (contains {:errors [{:new_password "New Password is a required field"}
                                  {:reset_token "A valid reset token is required"}]} :in-any-order)))

	(fact "Try resetting password of existing facebook user, should return 401 because Facebook users dont have one."
		(let [{:keys [email] :as user} (new-fb-user)
          _ (pav-req :put "/user/facebook" user)
					{status :status} (pav-req :post (str "/password/reset?email=" email))]
			status => 401))

	(fact "Given the users current password and a new password, Then change the users password and confirm authentication"
		(let [{:keys [password email] :as user} (new-pav-user)
          {body :body} (pav-req :put "/user" user)
					{token :token} body
					_ (pav-req :post "/password/change" token {:current_password password :new_password "password1"})
					{status :status} (pav-req :post "/user/authenticate" {:email email :password "password1"})]
			status => 201))

	(fact "Given the users current password and a new password, When current password is invalid, Then return 401 response"
		(let [user (new-pav-user)
          {body :body} (pav-req :put "/user" user)
          {token :token} body
					{status :status} (pav-req :post "/password/change" token {:current_password "invalid-password" :new_password "password1"})]
			status => 401))

	(fact "Changing password, when payload is missing new password, Then return 400 response"
		(let [{:keys [password] :as user} (new-pav-user)
          {body :body} (pav-req :put "/user" user)
          {token :token} body
					{status :status} (pav-req :post "/password/change" token {:current_password password})]
			status => 400))

	(fact "Changing password, when new password is empty, Then return 400 response"
		(let [{:keys [password] :as user} (new-pav-user)
          {body :body} (pav-req :put "/user" user)
          {token :token} body
					{status :status} (pav-req :post "/password/change" token {:current_password password :new_password ""})]
			status => 400)))