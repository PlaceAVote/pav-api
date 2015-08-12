(ns pav-user-api.utils.utils)

(defn record-in-ctx [ctx]
  (println "IN" ctx)
  (get ctx :record))

(defn retrieve-params [payload]
  (get-in payload [:body]))
