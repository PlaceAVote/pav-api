(ns com.pav.user.api.test.user.account-settings-test
	(:use midje.sweet)
	(:require [com.pav.user.api.test.utils.utils :refer [flush-redis
																											 flush-dynamo-tables
																											 flush-user-index
																											 test-user
																											 test-fb-user
																											 pav-req]]
						[cheshire.core :as ch]))

(against-background [(before :facts (do
																			(flush-dynamo-tables)
																			(flush-redis)
																			(flush-user-index)))]

	(fact "Retrieve a users account settings"
		(let [{body :body} (pav-req :put "/user" test-user)
					{token :token user_id :user_id} (ch/parse-string body true)
					{status :status body :body} (pav-req :get "/user/me/settings" token {})]
			status => 200
			(ch/parse-string body true) =>
				(merge {:user_id user_id :public true :social_login false}
					(select-keys test-user [:first_name :last_name :dob :gender :email :img_url]))))

	(fact "Retrieve a facebook users account settings"
		(let [{body :body} (pav-req :put "/user/facebook" test-fb-user)
					{token :token user_id :user_id} (ch/parse-string body true)
					{status :status body :body} (pav-req :get "/user/me/settings" token {})]
			status => 200
			(ch/parse-string body true) =>
			(merge {:user_id user_id :public true :social_login true}
				(select-keys test-fb-user [:first_name :last_name :dob :gender :email :img_url]))))

	(fact "Update a users account settings"
		(let [{body :body} (pav-req :put "/user" test-user)
					{token :token} (ch/parse-string body true)
					changes {:public false :first_name "Ted" :last_name "Baker" :gender "female" :dob "06/10/1986"
									 :email "Johnny5@placeavote.com" :city "New York City"}
					_ (pav-req :post "/user/me/settings" token changes)
					{status :status body :body} (pav-req :get "/user/me/settings" token {})]
			status => 200
			(ch/parse-string body true) => (contains changes)))

	(fact "Update a users account settings, when gender is invalid, return 400 error"
		(let [{body :body} (pav-req :put "/user" test-user)
					{token :token} (ch/parse-string body true)
					changes {:public false :first_name "Ted" :last_name "Baker" :gender "f" :dob "06/10/1986"
									 :email "Johnny5@placeavote.com" :city "New York City"}
					{status :status body :body} (pav-req :post "/user/me/settings" token changes)]
			(println body)
			status => 400
			(ch/parse-string body true) => {:errors [{:gender "Please specify a valid gender.  Valid values are male, female and they"}]}))

	(fact "Update a facebook users account settings"
		(let [{body :body} (pav-req :put "/user" test-user)
					{token :token} (ch/parse-string body true)
					changes {:public false :first_name "Ted" :last_name "Baker" :gender "female" :dob "06/10/1986"
									 :email "Johnny5@placeavote.com" :city "New York City"}
					_ (pav-req :post "/user/me/settings" token changes)
					{status :status body :body} (pav-req :get "/user/me/settings" token {})]
			status => 200
			(ch/parse-string body true) => (contains changes)))

	(fact "Try updating a users account settings, when given an invalid field.  Throw 400 error."
		(let [{body :body} (pav-req :put "/user" test-user)
					{token :token} (ch/parse-string body true)
					{status :status body :body} (pav-req :post "/user/me/settings" token {:invalid "invalidfield here"})]
			status => 400
			(ch/parse-string body true) => {:errors [{:invalid "field is unknown"}]}))

	(fact "Try updating user account with an invalid email, Throw 400 error"
		(let [{body :body} (pav-req :put "/user" test-user)
					{token :token} (ch/parse-string body true)
					{status :status} (pav-req :post "/user/me/settings" token {:email "john.com"})]
			status => 400))

	(fact "Try updating users email address.  Ensure you can login with new email address"
		(let [{body :body} (pav-req :put "/user" test-user)
					{token :token} (ch/parse-string body true)
					_ (pav-req :post "/user/me/settings" token {:email "newemail@placeavote.com"})
					{status :status} (pav-req :post "/user/authenticate" {:email "newemail@placeavote.com" :password (:password test-user)})]
			status => 201))

	(fact "Try updating user with empty payload. Ensure user remains untouched"
		(let [{body :body} (pav-req :put "/user" test-user)
					{token :token user_id :user_id} (ch/parse-string body true)
					_ (pav-req :post "/user/me/settings" token {})
					{status :status body :body} (pav-req :get "/user/me/settings" token {})]
			status => 200
			(ch/parse-string body true) =>
			(merge {:user_id user_id :public true :social_login false}
				(select-keys test-user [:first_name :last_name :dob :gender :email :img_url]))))

	(future-fact "Upload image."
		(let [{body :body} (pav-req :put "/user" test-user)
					{token :token user_id :user_id} (ch/parse-string body true)
					_ (pav-req :post "/user/me/profile/image" token {:file (slurp "test-resources/base64.jpeg")})
					{status :status body :body} (pav-req :get "/user/me/settings" token {})]
			status => 200
			(ch/parse-string body true) =>
			(merge {:user_id user_id :public true :social_login false}
						 (select-keys test-user [:first_name :last_name :dob :gender :email :img_url])))))


