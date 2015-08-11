(ns pav-user-api.test.utils.utils
  (require [clojurewerkz.neocons.rest :as nr]
           [clojurewerkz.neocons.rest.cypher :as cy]
           [pav-user-api.services.users :refer [create-user]]
           [environ.core :refer [env]]))

(def connection (nr/connect (:neo-url env) (:neo-username env) (:neo-password env)))

(def test-user {:email "johnny@stuff.com" :password "stuff"})
(defn create-user-accounts []
  (create-user test-user))

(defn bootstrap-users []
  (cy/query connection "MATCH (n) OPTIONAL MATCH (n)-[r]-() DELETE n,r")
  (create-user-accounts))
