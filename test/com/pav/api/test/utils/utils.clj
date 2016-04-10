(ns com.pav.api.test.utils.utils
  (require [com.pav.api.services.users :refer [create-user-profile]]
           [com.pav.api.services.questions :refer [bootstrap-wizard-questions]]
           [ring.mock.request :refer [request body content-type header]]
           [com.pav.api.handler :refer [app]]
           [cheshire.core :as ch]
           [environ.core :refer [env]]
           [taoensso.carmine :as car :refer (wcar)]
           [taoensso.faraday :as far]
           [msgpack.clojure-extensions]
           [clojurewerkz.elastisch.rest :refer [connect]]
           [clojurewerkz.elastisch.rest.index :as esi]
           [clojurewerkz.elastisch.rest.document :as esd]
           [clojurewerkz.elastisch.rest.bulk :as erb]
           [clojurewerkz.elastisch.common.bulk :as ecb]
           [clojure.edn :as edn]
           [com.pav.api.dynamodb.db :as db]))

(def test-user {:email "john@stuff.com" :password "stuff2" :first_name "john" :last_name "stuff" :dob "05/10/1984"
                :topics ["Defense"] :gender "male" :zipcode "12345"})

(def test-fb-user {:email "paul@facebook.com" :first_name "john" :last_name "stuff" :dob "05/10/1984"
                   :img_url "http://image.com/image.jpg" :topics ["Defense"] :token "token" :gender "male" :id "818181" :zipcode "12345"})

(def client-opts {:access-key "<AWS_DYNAMODB_ACCESS_KEY>"
                  :secret-key "<AWS_DYNAMODB_SECRET_KEY>"
                  :endpoint "http://localhost:8000"})

(def redis-conn {:spec {:host "127.0.0.1" :port 6379}})

(def es-connection (connect (:es-url env)))

(def test-bills [(ch/parse-string (slurp "test-resources/bills/hr2-114.json") true)
                 (ch/parse-string (slurp "test-resources/bills/hr1764-114.json") true)
                 (ch/parse-string (slurp "test-resources/bills/hr2029-114.json") true)
                 (ch/parse-string (slurp "test-resources/bills/hr4269-114.json") true)
                 (ch/parse-string (slurp "test-resources/bills/s25-114.json") true)])

(def bill-metadata [{:featured_img_link "https://upload.wikimedia.org/wikipedia/commons/c/cf/LAPD_Arrest_North_Hills.jpg"
                     :govtrack_link "govtrack link" :_id "hr2-114" :bill_id "hr2-114" :featured_bill_summary "hr2 summary"
                     :featured_bill_title "hr2 bill title" :pav_topic "Healthcare"
                     :points_against "Point against" :points_infavor "point infavour"
                     :congress "114" :pav_tags ["Healthcare"]}
                    {:featured_img_link "https://upload.wikimedia.org/wikipedia/commons/c/cf/LAPD_Arrest_North_Hills.jpg"
                     :govtrack_link "govtrack link" :_id "s25-114" :bill_id "s25-114" :featured_bill_summary "s25 summary"
                     :featured_bill_title "s25 bill title" :pav_topic "Economics"
                     :points_against "Point against" :points_infavor "point infavour"
                     :congress "114" :pav_tags ["Economics"]}])

(defn bootstrap-bills-and-metadata []
  (erb/bulk-with-index-and-type es-connection "congress" "bill"
    (ecb/bulk-index (map #(assoc % :_id (:bill_id %)) test-bills)) {:refresh true})
  (erb/bulk-with-index-and-type es-connection "congress" "billmeta" (ecb/bulk-index bill-metadata) {:refresh true}))

(def test-legislator (ch/parse-string (slurp "test-resources/legislators/data.json") true))

(defn bootstrap-legislators []
  (esd/put es-connection "congress" "legislator" (:thomas test-legislator) test-legislator {:refresh true}))

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

(defn flush-es-indexes []
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
