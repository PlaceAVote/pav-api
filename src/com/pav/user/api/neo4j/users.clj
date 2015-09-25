(ns com.pav.user.api.neo4j.users
  (:require [com.pav.user.api.database.database :refer [neo-connection]]
            [clojurewerkz.neocons.rest.nodes :as nrn]
            [clojurewerkz.neocons.rest.labels :as nrl]
            [clojure.tools.logging :as log]))


(defn create-user [user]
  (let [connection (neo-connection)
        user-without-pwd (dissoc user :password)
        new-user (nrn/create connection user-without-pwd)]
    (nrl/add connection new-user "User")
    (log/info (str "New User Persisted to Neo4J " user-without-pwd))))
