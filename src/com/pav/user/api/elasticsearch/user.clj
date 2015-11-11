(ns com.pav.user.api.elasticsearch.user
  (:require [clojurewerkz.elastisch.rest :refer [connect]]
            [clojurewerkz.elastisch.rest.document :as esd]
            [environ.core :refer [env]]))

(def connection (connect (:es-url env)))

(defn index-user [user]
  (esd/create connection "pav" "users" user :id (:user_id user)))
