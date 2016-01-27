(ns com.pav.user.api.utils.utils
  (:require [msgpack.core :as msg]
            [msgpack.clojure-extensions]
            [cheshire.core :as ch]))

(defn record-in-ctx [ctx]
  (get ctx :record))

(defn retrieve-body [payload]
  (or (get-in payload [:request :body]) {}))

(defn retrieve-body-param [payload param]
	(param (retrieve-body payload)))

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
	(-> (msg/unpack msg)
		  (ch/parse-string true)))

(defn to-json [msg]
	(ch/generate-string msg))

(defn has-keys?
  "Check if map has all specified keys."
  [mp keys]
  (apply = (map count [keys (select-keys mp keys)])))
