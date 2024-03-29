 (ns system
  (:require [com.pav.api.handler :refer [app start-server]]
            [com.pav.api.db.migrations :refer [migrate-db-on-startup]]
            [com.pav.api.dbwrapper.from-dynamodb :refer [migrate-all-data
                                                         convert-user-dobs-to-timestamps]]
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
   ["-f" "--format-user-dobs [option]" "Format existing users date of births from Strings to Timestamps."
    :parse-fn parse-args
    :default-desc "false"
    :default false]
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

(defmacro exec-cmd
  "Small utility macro that takes a pred and executes a series of functions specified."
  [pred & cmds]
  (list 'if pred (cons 'do cmds)))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)]
    (exec-cmd errors (exit 1 (error-msg errors)))
    (exec-cmd (:help options) (exit 1 (usage summary)))
    (exec-cmd (:drop-all-tables options) (drop-all-tables!))
    (exec-cmd (:migrate-database options) (migrate-db-on-startup))
    (exec-cmd (:create-dynamodb-tables options) (create-all-tables!))
    (exec-cmd (:migrate-data options) (migrate-all-data))
    (exec-cmd (:format-user-dobs options) (convert-user-dobs-to-timestamps))
    (exec-cmd
      (:start-server options)
      (start-server {:port 8080})
      (log/info "Server Listening on port 8080"))))
