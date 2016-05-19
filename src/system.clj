 (ns system
  (:require [com.pav.api.handler :refer [app start-server]]
            [com.pav.api.db.migrations :refer [migrate-db-on-startup]]
            [com.pav.api.dbwrapper.from-dynamodb :refer [migrate-all-data]]
            [com.pav.api.dynamodb.db :refer [create-all-tables!]]
            [com.pav.api.db.db :refer [drop-all-tables!]]
            [clojure.tools.cli :as cli]
            [clojure.tools.logging :as log]
            [clojure.string :as str])
   (:gen-class))

(defn- parse-args [arg]
  (if (some #{(str/trim arg)} ["1" "true" "yes"])
    true
    false))

(def cli-options
  [["-m" "--migrate-database [option]" "Run database migration."
    :parse-fn parse-args
    :default-desc "true"
    :default true]
   ["-o" "--create-dynamodb-tables [option]" "Create missing dynamodb tables."
    :parse-fn parse-args
    :default-desc "true"
    :default true]
   ["-d" "--migrate-data [option]" "Run data migrations."
    :parse-fn parse-args
    :default-desc "false"
    :default false]
   ["-t" "--drop-all-tables [option]" "Drop all tables in SQL database."
    :parse-fn parse-args
    :default-desc "false"
    :default false]
   ["-s" "--start-server [option]" "Start web servert on port 8080."
    :parse-fn parse-args
    :default-desc "true"
    :default true]
   ["-h" "--help" "Display help"]])

(defn- error-msg [errors]
  (str/join \newline errors))

(defn- usage [summary]
  (str
   "Usage: java -jar pav-api.jar [options]\n"
   "Options:\n"
   summary))

(defn- exit [status msg]
  (when msg (println msg))
  (System/exit status))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)]
    (cond
     (:help options)                   (exit 1 (usage summary))
     ;; NOTE: order is important
     (:drop-all-tables options)        (drop-all-tables!)
     (:migrate-database options)       (migrate-db-on-startup)
     (:create-dynamodb-tables options) (create-all-tables!)
     (:migrate-data options)           (migrate-all-data)
     errors                            (exit 1 (error-msg errors)))
    (when (:start-server options)
      (start-server {:port 8080})
      (log/info "Server Listening on port 8080"))))
