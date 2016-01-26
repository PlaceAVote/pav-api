(ns com.pav.user.api.utils.utils
	(:require [msgpack.core :as msg]
						[msgpack.clojure-extensions]
						[cheshire.core :as ch])
	(:import (java.io ByteArrayInputStream)
					 (org.apache.commons.codec.binary Base64)))

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
  (-> (retrieve-user-details payload)
      :email))

(defn retrieve-token-user-id [payload]
  (-> (retrieve-user-details payload)
      :user_id))

(defn unpack-redis-msg [msg]
	(-> (msg/unpack msg)
		  (ch/parse-string true)))

(defn to-json [msg]
	(ch/generate-string msg))

(defn decodeBase64ImageString [encoded-str]
	"Parse the encoded string for its content-type, size and contents."
	(when encoded-str
		(let [t (.split encoded-str ",")
					content-type (-> (re-seq #"(?<=:)(.*\n?)(?=;)" (first t)) flatten first)
					bytes (Base64/decodeBase64 (second t))
				 	size (count bytes)
				 	inputstream (ByteArrayInputStream. bytes)]
			{:content-type content-type
			:tempfile     inputstream
			:size         size})))