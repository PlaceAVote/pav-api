(ns com.pav.user.api.test.user.wizard-test
	(:use midje.sweet)
	(:require [cheshire.core :refer [parse-string]]
						[com.pav.user.api.test.utils.utils :refer [flush-dynamo-tables flush-redis create-questions
																											 flush-user-index bootstrap-bills pav-req]]))

(def test-user {:email "john@stuff.com" :password "stuff2" :first_name "john" :last_name "stuff" :dob "05/10/1984"
								:country_code "USA" :topics ["Gun Rights"] :gender "male"})

(def test-questions [{:question_id "1001" :question_type "slider" :topic "Gun Rights"
											:answers ["I want less gun control" "Not Sure" "I want more gun control"]}
										 {:question_id "1002" :question_type "slider" :topic "Defense"
											:answers ["I want less tanks" "Not Sure" "I want more more tanks"]}])

(def test-answers [{:question_id "1001" :answer ["I want more gun control"]}
									 {:question_id "1002" :answer ["I want less tanks"]}])

(against-background [(before :facts (do
																			(flush-dynamo-tables)
																			(flush-redis)
																			(flush-user-index)
																			(bootstrap-bills)))]
	(fact "Given a user token, Then retrieve questions associated with that users topic selection"
		(let [{body :body} (pav-req :put "/user" test-user)
					{token :token} (parse-string body true)
					_ (create-questions test-questions)
					{status :status body :body} (pav-req :get "/user/questions" token {})]
			status => 200
			(parse-string body true) => (contains (first test-questions))))

	(fact "Given answers to questions, Then any new retrieval of questions should be empty"
		(let [{body :body} (pav-req :put "/user" test-user)
					{token :token} (parse-string body true)
					_ (create-questions test-questions)
					_ (pav-req :post "/user/questions" token {:answers test-answers})
					{status :status body :body} (pav-req :get "/user/questions" token {})]
			status => 200
			(parse-string body true) => (contains (first test-questions)))))
