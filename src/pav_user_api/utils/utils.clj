(ns pav-user-api.utils.utils)

(defn record-in-ctx [ctx]
  (get ctx :record))

(defn retrieve-body [payload]
  (or (get-in payload [:body]) {}))

(defn retrieve-token [payload]
  ((:headers payload) "pav_auth_token"))
