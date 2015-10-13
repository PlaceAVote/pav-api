(ns com.pav.user.api.test.utils.utils
  (require [com.pav.user.api.services.users :refer [create-user]]
           [ring.mock.request :refer [request body content-type header]]
           [com.pav.user.api.handler :refer [app]]
           [cheshire.core :as ch]
           [environ.core :refer [env]]
           [com.pav.user.api.entities.user :refer [users user-token user-social-token]]
           [clojurewerkz.neocons.rest :refer [connect]]
           [clojurewerkz.neocons.rest.cypher :as nrc]
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

(def neo-connection (connect "http://localhost:7474/db/data/" "neo4j" "password"))

(defn delete-user-nodes []
  (try
    (nrc/query neo-connection "MATCH ()-[r]->() DELETE r")
    (nrc/query neo-connection "MATCH n DELETE n")
    (catch Exception e)))

(defn retrieve-user-from-neo [email]
  (nrc/query neo-connection "MATCH (user:User {email: {email}}) RETURN user.email AS email, user.first_name, user.last_name, user.dob, user.country_code, user.topics" {:email email}))

(defn delete-user-data []
  (delete users
          (where {:email [not= "null"]}))
  (delete user-token
          (where {:token [not= "null"]}))
  (delete user-social-token
          (where {:token [not= "null"]}))
  (delete-user-nodes))

(defn make-request
  ([method url payload]
    (app (content-type (request method url (ch/generate-string payload)) "application/json"))))

(defn parse-response-body [response]
  (ch/parse-string (:body response) true))