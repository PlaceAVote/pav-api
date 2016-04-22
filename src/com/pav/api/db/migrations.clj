(ns com.pav.api.db.migrations
  "Migrations helpers, powered with Flyway."
  (:require [clojure.tools.logging :as log]
            [com.pav.api.db.db :as db])
  (:import org.flywaydb.core.Flyway))

(defn- url-with-slash-slash?
  "Check if url starts with //."
  [url]
  (.startsWith url "//"))

(defn flyway-setup
  "Create and setup Flyway object. 'driver' object is the same
object format used by clojure.java.jdbc driver."
  [driver]
  (let [url (:subname driver)
        url (if-not (url-with-slash-slash? url)
              (throw (Exception. "Database url is malformed. Must start with '//"))
              (str "jdbc:mysql:" url))]
    (doto (Flyway.)
      (.setBaselineOnMigrate true)
      (.setDataSource url (:user driver) (:password driver) nil))))

(defn migrate!
  "Migrate curent database schema using Flyway. Migrations are read from 
'resources/db/migration' or 'db/migration' on classpath. 'obj' object is the same
object format used by clojure.java.jdbc driver, so we don't have to have duplicate
information."
  ([obj]
     (-> obj flyway-setup .migrate))
  ([] (migrate! db/db)))

(defn repair
  "Repair failed migration(s), by fixing Flyway metadata table. See
https://flywaydb.org/documentation/api/javadoc/org/flywaydb/core/Flyway.html#repair-- for detail
explanation."
  ([obj]
     (-> obj flyway-setup .repair))
  ([] (repair db/db)))

(defn- get-migration-detail
  "Return a map with the details about given migration."
  [mig]
  {:checksum (.getChecksum mig)
   :description (.getDescription mig)
   :execution_time (.getExecutionTime mig)
   :installed_by (.getInstalledBy mig)
   :installed_on (.getInstalledOn mig)
   :installed_rank (.getInstalledRank mig)
   :script (.getScript mig)
   :state (-> mig .getState .getDisplayName)
   :type (-> mig .getType str)
   :version (-> mig .getVersion str)})

(defn info
  "Return detail information about done or pending migrations."
  ([obj]
     (map get-migration-detail (-> obj flyway-setup .info .all)))
  ([] (info db/db)))
