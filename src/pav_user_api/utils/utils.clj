(ns pav-user-api.utils.utils)

(defn record-in-ctx [ctx]
  (get ctx :record))

(defn retrieve-params [payload]
  (or (get-in payload [:body]) {}))
