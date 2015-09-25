(ns com.pav.user.api.utils.utils)

(defn record-in-ctx [ctx]
  (get ctx :record))

(defn retrieve-body [payload]
  (or (get-in payload [:body]) {}))

(defn retrieve-user-details [payload]
  (:identity payload))
