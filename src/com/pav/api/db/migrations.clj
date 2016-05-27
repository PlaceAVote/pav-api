(ns com.pav.api.db.migrations
  "Migrations helpers, powered with Flyway."
  (:require [clojure.tools.logging :as log]
            [com.pav.api.db.db :as db])
  (:import org.flywaydb.core.Flyway))

(defn- fix-url
  "Flyway expects jdbc url to start with 'jdbc:'."
  [driver]
  (str "jdbc:" (:subprotocol driver) ":" (:subname driver)))

(defn flyway-setup
  "Create and setup Flyway object. 'driver' object is the same
object format used by clojure.java.jdbc driver. Returns Flyway object
that is able also to do migrations on database existed before Flyway
metadata table is created."
  [driver]
  (doto (Flyway.)
    (.setBaselineOnMigrate true)
    (.setDataSource (fix-url driver)
                    (:user driver)
                    (:password driver) nil)))

(defn migrate!
  "Migrate curent database schema using Flyway. Migrations are read from 
'resources/db/migration' or 'db/migration' on classpath. 'obj' object is the same
object format used by clojure.java.jdbc driver, so we don't have to have duplicate
information."
  ([obj]
     (-> obj flyway-setup .migrate))
  ([] (migrate! db/db)))

(defn repair!
  "Repair failed migration(s), by fixing Flyway metadata table. See
https://flywaydb.org/documentation/api/javadoc/org/flywaydb/core/Flyway.html#repair-- for detail
explanation."
  ([obj]
     (-> obj flyway-setup .repair))
  ([] (repair! db/db)))

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
   (let [i (map get-migration-detail (-> obj flyway-setup .info .all))]
     (log/info i)
     i))
  ([] (info db/db)))

(defn pending-migrations
  "Return details about pending migrations - migrations present on drive
but not applied on database. Returns nil if there are no pending migrations."
  ([obj]
     (seq
      (map get-migration-detail (-> obj flyway-setup .info .pending))))
  ([] (pending-migrations db/db)))

(defn migrate-db-on-startup []
  (log/info "Running DB Migration " (info))
  (repair!)
  (migrate!)
  (log/info "Finished DB Migration " (info)))
