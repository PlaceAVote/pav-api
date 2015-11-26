(ns com.pav.user.api.test.utils.utils
  (require [com.pav.user.api.services.users :refer [create-user-profile]]
           [ring.mock.request :refer [request body content-type header]]
           [com.pav.user.api.handler :refer [app]]
           [cheshire.core :as ch]
           [environ.core :refer [env]]
           [taoensso.carmine :as car :refer (wcar)]
           [taoensso.faraday :as far]
           [msgpack.core :as msg]
           [msgpack.clojure-extensions]
           [clojurewerkz.elastisch.rest :refer [connect]]
           [clojurewerkz.elastisch.rest.index :as esi]))

(def client-opts {:access-key "<AWS_DYNAMODB_ACCESS_KEY>"
                  :secret-key "<AWS_DYNAMODB_SECRET_KEY>"
                  :endpoint "http://localhost:8000"})

(def user-table-name (:dynamo-user-table-name env))
(def user-confirm-table-name (:dynamo-user-confirmation-table-name env))
(def notification-table-name (:dynamo-notification-table-name env))
(def follower-table-name (:dynamo-follower-table-name env))
(def following-table-name (:dynamo-following-table-name env))

(def redis-conn {:spec {:host "127.0.0.1" :port 6379}})

(def es-connection (connect (:es-url env)))

(defn flush-redis []
  (wcar redis-conn
        (car/flushall)
        (car/flushdb)))

(defn delete-user-table []
  (println "deleting table")
  (try
    (far/delete-table client-opts user-table-name)
    (far/delete-table client-opts user-confirm-table-name)
    (far/delete-table client-opts notification-table-name)
    (far/delete-table client-opts follower-table-name)
    (far/delete-table client-opts following-table-name)
    (catch Exception e (println "Error occured when deleting table " e " table name: " user-table-name " client-opts " client-opts))))

(defn create-user-table []
  (println "creating table")
  (try
    (far/create-table client-opts user-table-name [:user_id :s]
                      {:gsindexes [{:name "user-email-idx"
                                    :hash-keydef [:email :s]
                                    :throughput {:read 5 :write 10}}]
                       :throughput {:read 5 :write 10}
                       :block? true})
    (far/create-table client-opts user-confirm-table-name [:confirmation-token :s]
                      {:throughput {:read 5 :write 10}
                       :block? true})
    (far/create-table client-opts notification-table-name [:user_id :s]
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
    (catch Exception e (println "Error occured with table setup " e " table name: " user-table-name " client-opts " client-opts))))

(defn make-request
  ([method url payload]
    (app (content-type (request method url (ch/generate-string payload)) "application/json"))))

(defn parse-response-body [response]
  (ch/parse-string (:body response) true))

(defn persist-timeline-event [events]
  (wcar redis-conn
        (mapv (fn [event]
                (car/zadd (str "timeline:" (:user_id event)) (:timestamp event) (-> (ch/generate-string event)
                                                                                    msg/pack)))
              events)))

(defn flush-user-index []
  (esi/delete es-connection "pav")
  (esi/create es-connection "pav"))