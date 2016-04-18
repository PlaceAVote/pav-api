(ns com.pav.api.test.user.account-settings-test
	(:use midje.sweet)
    (:require [com.pav.api.test.utils.utils :refer [flush-redis
                                                    flush-dynamo-tables
                                                    flush-es-indexes
                                                    new-pav-user
                                                    new-fb-user
                                                    bootstrap-bills-and-metadata
                                                    pav-req]]))

(against-background [(before :contents (do
                                         (flush-dynamo-tables)
                                         (flush-redis)
                                         (flush-es-indexes)
                                         (bootstrap-bills-and-metadata)))]
	(fact "Retrieve a users account settings"
		(let [user (new-pav-user)
          {body :body} (pav-req :put "/user" user)
          {token :token user_id :user_id} body
          {status :status body :body} (pav-req :get "/user/me/settings" token {})]
			status => 200
			body =>
				(merge {:user_id user_id :public true :social_login false}
					(select-keys user [:first_name :last_name :dob :gender :email :img_url]))))

	(fact "Retrieve a facebook users account settings"
		(let [user (new-fb-user)
          {body :body} (pav-req :put "/user/facebook" user)
          {token :token user_id :user_id} body
          {status :status body :body} (pav-req :get "/user/me/settings" token {})]
			status => 200
			body => (merge {:user_id user_id :public true :social_login true}
                (select-keys user [:first_name :last_name :dob :gender :email :img_url]))))

	(fact "Update a users account settings"
		(let [user (new-pav-user)
          {body :body} (pav-req :put "/user" user)
          {token :token} body
          changes {:public false :first_name "Ted" :last_name "Baker" :gender "female" :dob "06/10/1986"
                   :email "Johnny5@placeavote.com" :city "New York City"}
          _ (pav-req :post "/user/me/settings" token changes)
					{status :status body :body} (pav-req :get "/user/me/settings" token {})]
			status => 200
			body => (contains changes)))

	(fact "Update a users account settings, when gender is invalid, return 400 error"
    (let [user (new-pav-user)
          {body :body} (pav-req :put "/user" user)
          {token :token} body
          changes {:public false :first_name "Ted" :last_name "Baker" :gender "f" :dob "06/10/1986"
                   :email "Johnny5@placeavote.com" :city "New York City"}
          {status :status body :body} (pav-req :post "/user/me/settings" token changes)]
      status => 400
      body => {:errors [{:gender "Please specify a valid gender.  Valid values are male, female and they"}]}))

	(fact "Update a facebook users account settings"
    (let [user (new-fb-user)
          {body :body} (pav-req :put "/user/facebook" user)
          {token :token} body
          changes {:public false :first_name "Ted" :last_name "Baker" :gender "female" :dob "06/10/1986"
                   :email  "Johnny5@placeavote.com" :city "New York City"}
          _ (pav-req :post "/user/me/settings" token changes)
          {status :status body :body} (pav-req :get "/user/me/settings" token {})]
      status => 200
      body => (contains changes)))

	(fact "Try updating a users account settings, when given an invalid field.  Throw 400 error."
        (let [{body :body} (pav-req :put "/user" (new-pav-user))
              {token :token} body
              {status :status body :body} (pav-req :post "/user/me/settings" token {:invalid "invalidfield here"})]
          status => 400
          body => {:errors [{:invalid "field is unknown"}]}))

  (fact "Try updating user account with an invalid email, Throw 400 error"
		(let [{body :body} (pav-req :put "/user" (new-pav-user))
          {token :token} body
          {status :status} (pav-req :post "/user/me/settings" token {:email "john.com"})]
          status => 400))

	(fact "Try updating users email address.  Ensure you can login with new email address"
		(let [{:keys [password] :as user} (new-pav-user)
          {body :body} (pav-req :put "/user" user)
          {token :token} body
          _ (pav-req :post "/user/me/settings" token {:email "newemail@placeavote.com"})
          {status :status} (pav-req :post "/user/authenticate" {:email "newemail@placeavote.com" :password password})]
      status => 201))

	(fact "Try updating user with empty payload. Ensure user remains untouched"
		(let [user (new-pav-user)
          {body :body} (pav-req :put "/user" user)
					{token :token user_id :user_id} body
					_ (pav-req :post "/user/me/settings" token {})
					{status :status body :body} (pav-req :get "/user/me/settings" token {})]
			status => 200
			body =>
			(merge {:user_id user_id :public true :social_login false}
				(select-keys user [:first_name :last_name :dob :gender :email :img_url])))))
