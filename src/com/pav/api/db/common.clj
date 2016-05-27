(ns com.pav.api.db.common)

(defn unclobify
  "Check if argument is org.h2.jdbc.JdbcClob and convert it to string.
Otherwise, return given argument as is."
  [mp]
  (reduce-kv
    (fn [m k v]
      (assoc m k (if (instance? org.h2.jdbc.JdbcClob v)
                   ;; simple (str ...) does not work, because it will
                   ;; return 'clobXX:' header
                   (.getSubString v 1 (.length v))
                   v)))
    {} mp))

(defn extract-value
  "Extract value returned from query result, where we are interested only
in value, but now query key. This works for counting clauses, finding id of
only one id or extracting generated id. Makes no sense for multiple results."
  [ret]
  (-> ret first first val))