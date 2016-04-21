(ns com.pav.api.db.migrations
  "Migrations helpers, powered with Flyway."
  (:require [clojure.tools.logging :as log]
            [com.pav.api.db.db :as db])
  (:import org.flywaydb.core.Flyway))

(defn- url-with-slash-slash?
  "Check if url starts with //."
  [url]
  (.startsWith url "//"))

(defn migrate!
  "Migrate curent database schema using Flyway. Migrations are read from 
'resources/db/migration' or 'db/migration' on classpath. 'obj' object is the same
object format used by clojure.java.jdbc driver, so we don't have to have duplicate
information."
  ([obj]
     (let [url (:subname obj)
           url (if-not (url-with-slash-slash? url)
                 (throw (Exception. "Database url is malformed. Must start with '//"))
                 (str "jdbc:mysql:" url))
           fly (doto (Flyway.)
                 (.setBaselineOnMigrate true)
                 (.setDataSource url (:user obj) (:password obj) nil))]
       (.migrate fly)))
  ([] (migrate! db/db)))
