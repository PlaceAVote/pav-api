(ns com.pav.api.test.utils.utils
  (require [com.pav.api.services.questions :refer [bootstrap-wizard-questions]]
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
           [com.pav.api.dynamodb.db :as db]
           [com.pav.api.db.migrations :as sql-migrations]
           [com.pav.api.db.db :as sql-db]
           [com.pav.api.dbwrapper.helpers :refer [with-sql-backend]]
           [com.pav.api.dbwrapper.user :as du]
           [com.pav.api.domain.user :refer [assoc-common-attributes]]
           [com.pav.api.utils.utils :refer [time-log]]))

(defn new-pav-user
  ([{:keys [email password first_name last_name dob topics gender zipcode]
     :or   {email      (str "test" (rand-int 1000) "@placeavote.com")
            password   "stuff2"
            first_name "john"
            last_name  "stuff"
            dob        "465782400000"
            topics     ["Defense"]
            gender     "male"
            zipcode    "90210"}}]
   {:email  email :password password :first_name first_name :last_name last_name :dob dob
    :topics topics :gender gender :zipcode zipcode})
  ([] (new-pav-user {})))

(defn new-fb-user
  ([{:keys [email id first_name last_name dob topics gender zipcode img_url token]
     :or   {email      (str "test" (rand-int 1000) "@placeavote.com")
            id         (str (rand-int 1000))
            first_name "john"
            last_name  "stuff"
            dob        "465782400000"
            topics     ["Defense"]
            gender     "male"
            zipcode    "12345"
            img_url    "http://image.com/image.jpg"
            token      "token"}}]
   {:email  email :id id :first_name first_name :last_name last_name :dob dob
    :topics topics :gender gender :zipcode zipcode :img_url "http://image.com/image.jpg" :token token})
  ([] (new-fb-user {})))

(defn- parse-response [response]
  (update-in response [:body] #(when (seq %) (ch/parse-string % true))))

(defn pav-req
  ([method url] (parse-response (app (content-type (request method url) "application/json"))))
  ([method url payload] (parse-response (app (content-type (request method url (ch/generate-string payload)) "application/json"))))
  ([method url token payload] (parse-response (app (content-type (header (request method url (ch/generate-string payload))
                                                    "Authorization" (str "PAV_AUTH_TOKEN " token)) "application/json")))))

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
  (time-log "flush-redis"
    (wcar redis-conn
          (car/flushall)
          (car/flushdb))))

(defn flush-dynamo-tables []
  (time-log "flush-dynamo-tables"
    (db/recreate-all-tables! client-opts)))

(defn flush-selected-dynamo-tables [tables]
  (time-log "flush-selected-dynamo-tables"
    (db/recreate-tables! client-opts tables)))

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
    (time-log "flush-es-indexes"
      (esi/delete es-connection "pav")
      (esi/delete es-connection "congress")
      (esi/create es-connection "pav")
      (esi/create es-connection "congress"))
    (catch Exception e
      (println "Error while connecting to ElasticSearch: " e))))

(defn flush-sql-tables []
  (with-sql-backend
    (time-log "flush-sql-tables"
      (sql-migrations/migrate!)
      (sql-db/empty-all-tables-unsafe!))))

(defn create-user []
  (-> (assoc-common-attributes (new-pav-user)) du/create-user))

(defn select-values
  "Return values in order for given keys. Removes nil."
  [mp ks]
  (remove nil?
    (reduce #(conj %1 (get mp %2)) [] ks)))
