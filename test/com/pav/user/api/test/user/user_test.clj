(ns com.pav.user.api.test.user.user-test
  (:use midje.sweet)
  (:require [com.pav.user.api.handler :refer [app]]
            [com.pav.user.api.test.utils.utils :refer [make-request parse-response-body
                                                       create-user-table
                                                       delete-user-table
                                                       flush-redis
                                                       persist-timeline-event
																											 persist-notification-event
                                                       flush-user-index
                                                       bootstrap-bills
																											 create-comment]]
            [ring.mock.request :refer [request body content-type header]]
            [com.pav.user.api.resources.user :refer [existing-user-error-msg login-error-msg]]
            [com.pav.user.api.authentication.authentication :refer [create-auth-token]]
            [cheshire.core :as ch]))

(defn pav-req
  ([method url] (app (content-type (request method url) "application/json")))
  ([method url payload] (app (content-type (request method url (ch/generate-string payload)) "application/json")))
  ([method url token payload] (app (content-type (header (request method url (ch/generate-string payload))
                                                         "Authorization" (str "PAV_AUTH_TOKEN " token)) "application/json"))))

(def test-user {:email "john@stuff.com" :password "stuff2" :first_name "john" :last_name "stuff" :dob "05/10/1984"
								:country_code "USA" :topics ["Defense"]})

(def test-fb-user {:email "paul@facebook.com" :first_name "john" :last_name "stuff" :dob "05/10/1984" :country_code "USA"
									 :img_url "http://image.com/image.jpg" :topics ["Defense"] :token "token"})

(against-background [(before :facts (do
                                      (delete-user-table)
                                      (create-user-table)
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

  (fact "Retrieve users notifications"
        (let [{body :body} (pav-req :put "/user" test-user)
							{token :token user_id :user_id} (ch/parse-string body true)
							notification-events [{:type "comment" :bill_id "s1182-114" :user_id user_id :timestamp 1446479124991 :comment_id "comment:1"
																		:bill_title "A bill to exempt application of JSA attribution rule in case of existing agreements."
																		:score 0 :body "Comment text goes here!!" :event_id "10"}
																	 {:type "vote" :bill_id "s1182-114" :user_id user_id
																		:bill_title "A bill to exempt application of JSA attribution rule in case of existing agreements."
																		:timestamp 1446462364297 :event_id "11"}]
							_ (persist-notification-event notification-events)
							{status :status body :body} (pav-req :get "/user/notifications" token {})
							{next-page :next-page results :results} (ch/parse-string body true)]
					status => 200
					next-page => 0
					results => (contains notification-events)))

	(fact "Retrieve user notifications, mark notification as read"
		(let [{body :body} (pav-req :put "/user" test-user)
					{token :token user_id :user_id} (ch/parse-string body true)
					notification-events [{:type "comment" :bill_id "s1182-114" :user_id user_id :timestamp 1446479124991 :comment_id "comment:1"
																:bill_title "A bill to exempt application of JSA attribution rule in case of existing agreements."
																:score 0 :body "Comment text goes here!!" :event_id "10"}
															 {:type "vote" :bill_id "s1182-114" :user_id user_id
																:bill_title "A bill to exempt application of JSA attribution rule in case of existing agreements."
																:timestamp 1446462364297 :event_id "11"}]
					_ (persist-notification-event notification-events)
					{status :status} (pav-req :post "/user/notification/10/mark" token {})
					{body :body} (pav-req :get "/user/notifications" token {})
					{next-page :next-page results :results} (ch/parse-string body true)]
			status => 201
			next-page => 0
			(first results) => (contains {:read true})))

  (fact "Retrieving user notifications without Authentication token, results in 401"
        (let [{status :status} (pav-req :get "/user/notifications" "token" {})]
          status => 401))

  (fact "Try Retrieving users profile timeline with invalid token"
        (let [{status :status} (pav-req :get "/user/me/timeline" "rubbish token" {})]
          status => 401))

  (fact "Retrieve current users activity timeline"
        (let [{body :body} (pav-req :put "/user" test-user)
              {token :token user_id :user_id} (ch/parse-string body true)
              timeline-events [{:type "comment" :bill_id "s1182-114" :user_id user_id :timestamp 1446479124991 :comment_id "comment:1"
                                :bill_title "A bill to exempt application of JSA attribution rule in case of existing agreements."
                                :score 0 :body "Comment text goes here!!"}
                               {:type "vote" :bill_id "s1182-114" :user_id user_id
                                :bill_title "A bill to exempt application of JSA attribution rule in case of existing agreements."
                                :timestamp 1446462364297}]
              _ (persist-timeline-event timeline-events)
              {status :status body :body} (pav-req :get "/user/me/timeline" token {})
              {next-page :next-page results :results} (ch/parse-string body true)]
          status => 200
          next-page => 0
          results => (contains timeline-events)))

  (fact "Retrieve a users activity timeline"
        (let [{body :body} (pav-req :put "/user" test-user)
              {token :token} (ch/parse-string body true)
              timeline-events [{:type "comment" :bill_id "s1182-114" :user_id "user102" :timestamp 1446479124991 :comment_id "comment:1"
                                :bill_title "A bill to exempt application of JSA attribution rule in case of existing agreements."
                                :score 0 :body "Comment text goes here!!"}
                               {:type "vote" :bill_id "s1182-114" :user_id "user102"
                                :bill_title "A bill to exempt application of JSA attribution rule in case of existing agreements."
                                :timestamp 1446462364297}]
              _ (persist-timeline-event timeline-events)
              {status :status body :body} (pav-req :get "/user/user102/timeline" token {})
              {next-page :next-page results :results} (ch/parse-string body true)]
          status => 200
          next-page => 0
          results => (contains timeline-events)))

  (fact "Retrieve a users profile in relation to current user"
        (let [{caller :body} (pav-req :put "/user" test-user)
              {token :token} (ch/parse-string caller true)
              {search-user :body} (pav-req :put "/user" {:email "peter@pl.com"
                                                  :password "stuff2"
                                                  :first_name "peter" :last_name "pan"
                                                  :dob "05/10/1984"
                                                  :country_code "USA"
                                                  :topics ["Defense"]})
              {user_id :user_id} (ch/parse-string search-user true)
              _ (pav-req :put (str "/user/follow") token {:user_id user_id})
              {status :status body :body} (pav-req :get (str "/user/" user_id "/profile") token {})]
          status => 200
          (ch/parse-string body true) => (merge (dissoc (ch/parse-string search-user true) :email :topics :token)
                                                {:following true
                                                 :total_followers 1
                                                 :total_following 0})))

  (fact "Retrieve the current users profile"
        (let [{caller :body} (pav-req :put "/user" test-user)
              {token :token} (ch/parse-string caller true)
              {status :status body :body} (pav-req :get "/user/me/profile" token {})]
          status => 200
          (ch/parse-string body true) => (merge (dissoc (ch/parse-string caller true) :email :topics :token)
                                                {:total_followers 0
                                                 :total_following 0})))

  (fact "Follow/following another user"
        (let [{follower :body} (pav-req :put "/user" test-user)
              {my_id :user_id token :token} (ch/parse-string follower true)
              {being-followed :body} (pav-req :put "/user" {:email "peter@pl.com" :password "stuff2"
                                                         :first_name "peter" :last_name "pan"
                                                         :dob "05/10/1984" :country_code "USA"
                                                         :topics ["Defense"]})
              {pauls_user_id :user_id} (ch/parse-string being-followed true)
              {create_status :status} (pav-req :put (str "/user/follow") token {:user_id pauls_user_id})
              {my-following :body} (pav-req :get (str "/user/me/following") token {})
              {paul-following :body} (pav-req :get (str "/user/" pauls_user_id "/following") token {})
              {my-followers :body} (pav-req :get (str "/user/me/followers") token {})
              {pauls-followers :body} (pav-req :get (str "/user/" pauls_user_id "/followers") token {})]
          create_status => 201
          (ch/parse-string my-following true) => (contains {:user_id pauls_user_id :first_name "peter" :last_name "pan" :img_url nil
																														:follower_count 1})
          (ch/parse-string paul-following true) => []
          (ch/parse-string my-followers true) => []
          (ch/parse-string pauls-followers true) => (contains {:user_id my_id :first_name "john" :last_name "stuff" :img_url nil})))

  (fact "Unfollow user"
        (let [{follower :body} (pav-req :put "/user" test-user)
              {token :token} (ch/parse-string follower true)
              {being-followed :body} (pav-req :put "/user" {:email "peter@pl.com" :password "stuff2"
                                                            :first_name "peter" :last_name "pan"
                                                            :dob "05/10/1984" :country_code "USA"
                                                            :topics ["Defense"]})
              {pauls_user_id :user_id} (ch/parse-string being-followed true)
              _ (pav-req :put (str "/user/follow") token {:user_id pauls_user_id})
              {unfollow-status :status} (pav-req :delete (str "/user/unfollow") token {:user_id pauls_user_id})
              {my-following :body} (pav-req :get (str "/user/me/following") token {})
              {pauls-followers :body} (pav-req :get (str "/user/" pauls_user_id "/followers") token {})]
          unfollow-status => 204
          (ch/parse-string my-following true) => []
          (ch/parse-string pauls-followers true) => []))

  (fact "Verify the JWT Token"
        (let [{follower :body} (pav-req :put "/user" test-user)
              {token :token} (ch/parse-string follower true)
              {status :status} (pav-req :get (str "/user/token/validate?token=" token))]
          status => 200))

  (fact "Verify the JWT Token, when invalid, return 401."
       (let [{status :status} (pav-req :get "/user/token/validate?token=rubbish")]
        status => 401))

	(fact "Retrieve current users feed"
		(let [_ (Thread/sleep 3000)
					{body :body} (pav-req :put "/user" (assoc test-user :topics ["Defense" "Politics"]))
					{token :token} (ch/parse-string body true)
					_ (create-comment {:comment_id "101" :bill_id "hr1764-114"})
					_ (Thread/sleep 1000)
					{status :status body :body} (pav-req :get "/user/feed" token {})
					{next-page :next-page results :results} (ch/parse-string body true)]
			status => 200
			next-page => 0)))