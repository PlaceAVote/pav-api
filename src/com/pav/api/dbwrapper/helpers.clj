(ns com.pav.api.dbwrapper.helpers
  "Some helpers for turning on/off sql backend."
  (:require [environ.core :refer [env]]))

(def ^{:dynamic true
       :doc "Setting this to true, will enable storage to SQL backend. By default,
SQL storage is disabled."}
  *enable-sql-backend* (Boolean/valueOf (:sql-backend-enabled env)))

(defmacro with-sql-backend
  "Wrapper around *enable-sql-backend*."
  [& body]
  `(when *enable-sql-backend*
     ~@body))

(defmacro with-sql-backend-enabled
  "Same as 'with-sql-backend', but with '*enable-sql-backend*' set to true."
  [& body]
  `(binding [*enable-sql-backend* true]
     (with-sql-backend
       ~@body)))

(defn bigint->long
  "Convert from Clojure's BigInt to long if possible."
  [n]
  (if (instance? clojure.lang.BigInt n)
    (.longValue n)
    n))
