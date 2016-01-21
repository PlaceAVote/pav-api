(ns com.pav.user.api.test.utils.utils
  (require [com.pav.user.api.services.users :refer [create-user-profile]]
					 [com.pav.user.api.services.questions :refer [bootstrap-wizard-questions]]
           [ring.mock.request :refer [request body content-type header]]
           [com.pav.user.api.handler :refer [app]]
           [cheshire.core :as ch]
           [environ.core :refer [env]]
           [taoensso.carmine :as car :refer (wcar)]
           [taoensso.faraday :as far]
           [msgpack.clojure-extensions]
           [clojurewerkz.elastisch.rest :refer [connect]]
           [clojurewerkz.elastisch.rest.index :as esi]
					 [clojurewerkz.elastisch.rest.document :as esd]
					 [clojure.edn :as edn]))

(def test-user {:email "john@stuff.com" :password "stuff2" :first_name "john" :last_name "stuff" :dob "05/10/1984"
								:country_code "USA" :topics ["Defense"] :gender "male"})

(def test-fb-user {:email "paul@facebook.com" :first_name "john" :last_name "stuff" :dob "05/10/1984" :country_code "USA"
									 :img_url "http://image.com/image.jpg" :topics ["Defense"] :token "token" :gender "male" :id "818181"})

(def client-opts {:access-key "<AWS_DYNAMODB_ACCESS_KEY>"
                  :secret-key "<AWS_DYNAMODB_SECRET_KEY>"
                  :endpoint "http://localhost:8000"})

(def user-table-name (:dynamo-user-table-name env))
(def user-confirm-table-name (:dynamo-user-confirmation-table-name env))
(def notification-table-name (:dynamo-notification-table-name env))
(def follower-table-name (:dynamo-follower-table-name env))
(def following-table-name (:dynamo-following-table-name env))
(def timeline-table-name (:dynamo-usertimeline-table-name env))
(def feed-table-name (:dynamo-userfeed-table-name env))
(def comment-details-table-name (:dynamo-comment-details-table-name env))
(def vote-count-table-name (:dynamo-vote-count-table env))
(def question-table-name (:dynamo-questions-table env))
(def user-question-answers-table-name (:dynamo-user-question-answers-table env))

(def redis-conn {:spec {:host "127.0.0.1" :port 6379}})

(def es-connection (connect (:es-url env)))

(def test-bills [(ch/parse-string (slurp "test-resources/bills/hr2-114.json") true)
								 (ch/parse-string (slurp "test-resources/bills/hr1764-114.json") true)
								 (ch/parse-string (slurp "test-resources/bills/hr2029-114.json") true)
								 (ch/parse-string (slurp "test-resources/bills/s25-114.json") true)])

(defn bootstrap-bills []
	(doseq [bill test-bills]
		(esd/create es-connection "congress" "bill" bill :id (:bill_id bill))))

(def wizard-questions (edn/read-string (slurp "resources/questions.edn")))

(defn bootstrap-questions []
	(bootstrap-wizard-questions wizard-questions))

(defn flush-redis []
  (wcar redis-conn
        (car/flushall)
        (car/flushdb)))

(defn delete-dynamo-tables []
  (try
    (far/delete-table client-opts user-table-name)
    (far/delete-table client-opts user-confirm-table-name)
    (far/delete-table client-opts notification-table-name)
    (far/delete-table client-opts timeline-table-name)
    (far/delete-table client-opts follower-table-name)
    (far/delete-table client-opts following-table-name)
		(far/delete-table client-opts feed-table-name)
		(far/delete-table client-opts vote-count-table-name)
		(far/delete-table client-opts comment-details-table-name)
		(far/delete-table client-opts question-table-name)
		(far/delete-table client-opts user-question-answers-table-name)
    (catch Exception e (println "Error occured when deleting table " e " table name: " user-table-name " client-opts " client-opts))))

(defn create-dynamo-tables []
  (try
    (far/create-table client-opts user-table-name [:user_id :s]
                      {:gsindexes [{:name "user-email-idx"
                                    :hash-keydef [:email :s]
                                    :throughput {:read 5 :write 10}}
																	 {:name "fbid-idx"
																		:hash-keydef [:facebook_id :s]
																		:throughput {:read 5 :write 10}}]
                       :throughput {:read 5 :write 10}
                       :block? true})
    (far/create-table client-opts user-confirm-table-name [:confirmation-token :s]
                      {:throughput {:read 5 :write 10}
                       :block? true})
    (far/create-table client-opts notification-table-name [:user_id :s]
                      {:gsindexes [{:name "notification_id-idx"
																		:hash-keydef [:notification_id :s]
																		:throughput {:read 5 :write 10}}]
											 :range-keydef [:timestamp :n]
                       :throughput {:read 5 :write 10}
                       :block? true})
		(far/create-table client-opts timeline-table-name [:user_id :s]
                      {:range-keydef [:timestamp :n]
                       :throughput {:read 5 :write 10}
                       :block? true})
		(far/create-table client-opts feed-table-name [:user_id :s]
                      {:range-keydef [:timestamp :n]
                       :throughput {:read 5 :write 10}
                       :block? true})
    (far/create-table client-opts following-table-name [:user_id :s]
                      {:range-keydef [:following :s]
                       :throughput {:read 5 :write 10}
                       :block? true})
    (far/create-table client-opts follower-table-name [:user_id :s]
                      {:range-keydef [:follower :s]
                       :throughput {:read 5 :write 10}
                       :block? true})
		(far/create-table client-opts vote-count-table-name [:bill_id :s]
			{:throughput {:read 5 :write 10}
			 :block? true})
		(far/create-table client-opts comment-details-table-name [:comment_id :s]
			{:gsindexes [{:name "bill-comment-idx"
										:hash-keydef [:bill_id :s]
										:range-keydef [:comment_id :s]
										:throughput {:read 5 :write 10}}]
			 :throughput {:read 5 :write 10}
			 :block? true})
		(far/create-table client-opts question-table-name [:topic :s]
			{:range-keydef [:question_id :s]
			 :throughput {:read 5 :write 10}
			 :block? true})
		(far/create-table client-opts user-question-answers-table-name [:user_id :s]
			{:range-keydef [:question_id :s]
			 :throughput {:read 5 :write 10}
			 :block? true})
    (catch Exception e (println "Error occured with table setup " e " table name: " user-table-name " client-opts " client-opts))))

(defn flush-dynamo-tables []
  (delete-dynamo-tables)
  (create-dynamo-tables))

(defn make-request
  ([method url payload]
    (app (content-type (request method url (ch/generate-string payload)) "application/json"))))

(defn parse-response-body [response]
  (ch/parse-string (:body response) true))

(defn persist-timeline-event [events]
  (doseq [event events]
    (far/put-item client-opts timeline-table-name event)))

(defn persist-notification-event [events]
	(doseq [event events]
		(far/put-item client-opts notification-table-name event)))

(defn create-comment [comment]
	(far/put-item client-opts comment-details-table-name comment))

(defn flush-user-index []
  (esi/delete es-connection "pav")
  (esi/delete es-connection "congress")
  (esi/create es-connection "pav")
  (esi/create es-connection "congress"))


(defn pav-req
	([method url] (app (content-type (request method url) "application/json")))
	([method url payload] (app (content-type (request method url (ch/generate-string payload)) "application/json")))
	([method url token payload] (app (content-type (header (request method url (ch/generate-string payload))
																									 "Authorization" (str "PAV_AUTH_TOKEN " token)) "application/json"))))