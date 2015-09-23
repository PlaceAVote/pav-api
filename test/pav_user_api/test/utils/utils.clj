(ns pav-user-api.test.utils.utils
  (require [pav-user-api.services.users :refer [create-user]]
           [ring.mock.request :refer [request body content-type header]]
           [pav-user-api.handler :refer [app]]
           [cheshire.core :as ch]
           [environ.core :refer [env]]
           [pav-user-api.entities.user :refer [users user-token user-social-token]]
           [clojurewerkz.neocons.rest :refer [connect]]
           [clojurewerkz.neocons.rest.cypher :as nrc])
  (:use korma.core))

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