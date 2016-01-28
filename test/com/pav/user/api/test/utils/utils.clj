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
           [clojure.edn :as edn]
           [com.pav.user.api.dynamodb.db :as db]))

(def test-user {:email "john@stuff.com" :password "stuff2" :first_name "john" :last_name "stuff" :dob "05/10/1984"
                :country_code "USA" :topics ["Defense"] :gender "male"})

(def test-fb-user {:email "paul@facebook.com" :first_name "john" :last_name "stuff" :dob "05/10/1984" :country_code "USA"
                   :img_url "http://image.com/image.jpg" :topics ["Defense"] :token "token" :gender "male" :id "818181"})

(def client-opts {:access-key "<AWS_DYNAMODB_ACCESS_KEY>"
                  :secret-key "<AWS_DYNAMODB_SECRET_KEY>"
                  :endpoint "http://localhost:8000"})

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

(defn flush-dynamo-tables []
  (db/delete-all-tables! client-opts)
  (db/create-all-tables! client-opts))

(defn make-request
  ([method url payload]
    (app (content-type (request method url (ch/generate-string payload)) "application/json"))))

(defn parse-response-body [response]
  (ch/parse-string (:body response) true))

(defn persist-timeline-event [events]
  (doseq [event events]
    (far/put-item client-opts db/timeline-table-name event)))

(defn persist-notification-event [events]
  (doseq [event events]
    (far/put-item client-opts db/notification-table-name event)))

(defn create-comment [comment]
  (far/put-item client-opts db/comment-details-table-name comment))

(defn flush-user-index []
  (try
    (esi/delete es-connection "pav")
    (esi/delete es-connection "congress")
    (esi/create es-connection "pav")
    (esi/create es-connection "congress")
    (catch Exception e
      (println "Error while connecting to ElasticSearch: " e))))

(defn pav-req
  ([method url] (app (content-type (request method url) "application/json")))
  ([method url payload] (app (content-type (request method url (ch/generate-string payload)) "application/json")))
  ([method url token payload] (app (content-type (header (request method url (ch/generate-string payload))
                                                         "Authorization" (str "PAV_AUTH_TOKEN " token)) "application/json"))))
