(ns com.pav.api.utils.utils
	(:require [msgpack.core :as msg]
						[msgpack.clojure-extensions]
						[cheshire.core :as ch])
  (:import (java.io ByteArrayInputStream)
           (org.apache.commons.codec.binary Base64)
           (java.nio ByteBuffer)
           (java.util UUID)))

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

(defn pack-event [evt]
  (-> (ch/generate-string evt) msg/pack))

(defn unpack-redis-msg [msg]
  (-> msg
      msg/unpack
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

(defn uuid->base64Str
  "Converts UUID into 32 Character Base64 String"
  [uuid]
  (let [byte-buff (ByteBuffer/wrap (byte-array 16))]
    (.putLong byte-buff (.getMostSignificantBits uuid))
    (.putLong byte-buff (.getLeastSignificantBits uuid))
    (Base64/encodeBase64URLSafeString (.array byte-buff))))

(defn base64->uuidStr
  "Convert Base64 String to UUID String"
  [val]
  (let [byte-buffer (ByteBuffer/wrap (Base64/decodeBase64 val))]
    (.toString (UUID. (.getLong byte-buffer) (.getLong byte-buffer)))))
