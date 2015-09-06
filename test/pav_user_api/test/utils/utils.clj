(ns pav-user-api.test.utils.utils
  (require [pav-user-api.services.users :refer [create-user]]
           [ring.mock.request :refer [request body content-type header]]
           [pav-user-api.handler :refer [app]]
           [cheshire.core :as ch]
           [environ.core :refer [env]]
           [pav-user-api.entities.user :refer [users user-token]])
  (:use korma.core))

(def test-user-result {:email "johnny@stuff.com" })


(defn delete-user []
  (delete users
          (where {:email [not= "null"]}))
  (delete user-token
          (where {:token [not= "null"]})))

(defn make-request
  ([method url payload]
    (app (content-type (request method url (ch/generate-string payload)) "application/json"))))

(defn parse-response-body [response]
  (ch/parse-string (:body response) true))