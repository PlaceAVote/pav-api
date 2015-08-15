(ns pav-user-api.test.utils.utils
  (require [clojurewerkz.neocons.rest :as nr]
           [clojurewerkz.neocons.rest.constraints :as nc]
           [clojurewerkz.neocons.rest.cypher :as cy]
           [pav-user-api.services.users :refer [create-user]]
           [environ.core :refer [env]]))

(def connection (nr/connect (str "http://" (:neo4j-port-7474-tcp-addr env) ":" (:neo4j-port-7474-tcp-port env) "/db/data") (:neo-username env) (:neo-password env)))

(def test-user {:email "johnny@stuff.com" :password "stuff"})
(def test-user-result {:email "johnny@stuff.com" })

(defn create-user-accounts []
  (create-user test-user))

(defn bootstrap-constraints []
  (try
    (nc/create-unique connection "User" "email")
  (catch Exception e )))

(defn bootstrap-users []
  (cy/query connection "MATCH (n) OPTIONAL MATCH (n)-[r]-() DELETE n,r")
  (create-user-accounts))
