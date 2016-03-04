(ns com.pav.user.api.test.wizard.wizard-test
  (:use midje.sweet)
  (:require [cheshire.core :refer [parse-string]]
            [com.pav.user.api.test.utils.utils :refer [flush-dynamo-tables flush-redis
                                                       flush-es-indexes bootstrap-bills
                                                       bootstrap-questions wizard-questions
                                                       pav-req]]))

(def test-user {:email "john@stuff.com" :password "stuff2" :first_name "john" :last_name "stuff" :dob "05/10/1984"
                :country_code "USA" :topics ["Gun Rights"] :gender "male" :zipcode "12345"})

(def test-answers [{:question_id "1001" :answer ["I want more gun control"]}
                   {:question_id "1002" :answer ["I want less tanks"]}
                   {:question_id "1003" :answer [193000 ""]}])

(against-background [(before :facts (do
                                      (flush-dynamo-tables)
                                      (flush-redis)
                                      (flush-es-indexes)
                                      (bootstrap-bills)
                                      (bootstrap-questions)))]
	(fact "Given a user token, Then retrieve questions associated with that users topic selection"
		(let [{body :body} (pav-req :put "/user" test-user)
					{token :token} (parse-string body true)
					{status :status body :body} (pav-req :get "/user/questions" token {})
					parsed-body (parse-string body true)]
			status => 200
			(count parsed-body) => 1
			parsed-body => (contains (first wizard-questions))))

	(fact "Given a user token, When user has multiple topics, Then retrieve questions associated with that users topic selection"
		(let [{body :body} (pav-req :put "/user" (assoc test-user :topics ["Defense" "Gun Rights"]))
					{token :token} (parse-string body true)
					{status :status body :body} (pav-req :get "/user/questions" token {})
					parsed-body (parse-string body true)]
			status => 200
			(count parsed-body) => 2))

	(fact "Given a user token, When no questions exist for the user, Return an empty list"
		(let [{body :body} (pav-req :put "/user" (assoc test-user :topics ["BLABLA"]))
					{token :token} (parse-string body true)
					{status :status body :body} (pav-req :get "/user/questions" token {})]
			status => 200
			(parse-string body true) => []))

	(fact "Given answers to questions, Then any new retrieval of questions should be empty"
		(let [{body :body} (pav-req :put "/user" test-user)
					{token :token} (parse-string body true)
					_ (pav-req :post "/user/questions" token {:answers test-answers})
					{status :status body :body} (pav-req :get "/user/questions" token {})]
			status => 200
			(parse-string body true) => (contains (first wizard-questions)))))
