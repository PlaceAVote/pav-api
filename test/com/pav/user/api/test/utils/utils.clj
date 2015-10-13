(ns com.pav.user.api.test.utils.utils
  (require [com.pav.user.api.services.users :refer [create-user]]
           [ring.mock.request :refer [request body content-type header]]
           [com.pav.user.api.handler :refer [app]]
           [cheshire.core :as ch]
           [environ.core :refer [env]]
           [taoensso.carmine :as car :refer (wcar)]
           [taoensso.faraday :as far])
  (:use korma.core))

(def client-opts {:access-key "<AWS_DYNAMODB_ACCESS_KEY>"
                  :secret-key "<AWS_DYNAMODB_SECRET_KEY>"
                  :endpoint "http://localhost:8000"})

(defn delete-user-table []
  (try
    (far/delete-table client-opts :users)
    (catch Exception e (println "Error occured with table setup " e))))

(defn create-user-table []
  (try
    (far/create-table client-opts :users [:email :s]
                      {:throughput {:read 5 :write 10}
                       :block? true})
    (catch Exception e (println "Error occured with table setup " e))))

(defn make-request
  ([method url payload]
    (app (content-type (request method url (ch/generate-string payload)) "application/json"))))

(defn parse-response-body [response]
  (ch/parse-string (:body response) true))