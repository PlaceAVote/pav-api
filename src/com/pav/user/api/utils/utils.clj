(ns com.pav.user.api.utils.utils
	(:require [msgpack.core :as msg]
						[msgpack.clojure-extensions]
						[cheshire.core :as ch]))

(defn record-in-ctx [ctx]
  (get ctx :record))

(defn retrieve-body [payload]
  (or (get-in payload [:body]) {}))

(defn retrieve-user-details [payload]
  (:identity payload))

(defn retrieve-user-email [payload]
  (-> (retrieve-user-details payload)
      :email))

(defn retrieve-user-id [payload]
  (-> (retrieve-user-details payload)
      :user_id))

(defn unpack-redis-msg [msg]
	(-> (msg/unpack msg)
		  (ch/parse-string true)))

(defn to-json [msg]
	(ch/generate-string msg))
