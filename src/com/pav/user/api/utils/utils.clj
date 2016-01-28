(ns com.pav.user.api.utils.utils
  (:require [msgpack.core :as msg]
            [msgpack.clojure-extensions]
            [cheshire.core :as ch]))

(defn record-in-ctx [ctx]
  (get ctx :record))

(defn retrieve-body [payload]
  (or (get-in payload [:request :body]) {}))

(defn retrieve-body-param [payload param]
  (-> payload
      retrieve-body
      param))

(defn retrieve-request-param [payload param]
  (get-in payload [:request :params param]))

(defn retrieve-user-details [payload]
  (get-in payload [:request :identity]))

(defn retrieve-user-email [payload]
  (some-> payload
          retrieve-user-details
          :email))

(defn retrieve-token-user-id [payload]
  (some-> payload
          retrieve-user-details
          :user_id))

(defn unpack-redis-msg [msg]
  (-> msg
      msg/unpack
      (ch/parse-string true)))

(defn to-json [msg]
  (ch/generate-string msg))

(defn has-keys?
  "Check if map has all specified keys."
  [mp ks]
  (apply = (map count [ks (select-keys mp ks)])))

(defn has-only-keys?
  "Check if only specified keys are present in map."
  [mp ks]
  (and (= (sort ks)
          (-> mp keys sort))
       (has-keys? mp ks)))
