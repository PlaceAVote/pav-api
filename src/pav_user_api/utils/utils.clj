(ns pav-user-api.utils.utils)

(defn record-in-ctx
  [ctx]
  (get ctx :record))

(defn retrieve-params [payload]
  (:params (select-keys payload [:params])))
