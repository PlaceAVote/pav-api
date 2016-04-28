(ns com.pav.api.utils.utils
  (:require [msgpack.core :as msg]
            [msgpack.clojure-extensions]
            [clj-http.client :as http]
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

(defn is-java7-or-6?
  "Returns true if we are running on Java 7 or 6 (7 is first checks, since it is highly a chance we will hit it).
This is used for SSL workaround for sunlightfoundation.com site."
  []
  (let [version (System/getProperty "java.version")]
     (or (.startsWith version "1.7")
         (.startsWith version "1.6"))))

(defn http-to-json
  "Retrieve url and convert result to JSON. Since Java 7 does not support
SSL on sunlightfoundation, 'nossl?' parameter should be set to true. 'url' part should be
without 'http(s)://'."
  ([url nossl?]
     (-> (str (if nossl? "http" "https") "://" url)
         http/get
         :body
         (ch/parse-string true)
         :results
         first))
  ([url] (http-to-json url false)))

(defn http-to-json-sunlightfoundation-api
  "Call http-to-json on congress.api.sunlightfoundation.com/suburl, choosing between
http or https, depending on Java version. Make sure suburl to start with slash."
  [suburl]
  (http-to-json (str "congress.api.sunlightfoundation.com" suburl) (is-java7-or-6?)))

(defn reload-env!
  "Force 'env' to be reloaded. This is using some trickery behind clojure's back
and should be used only from REPL (that is mainly designed for).

'env' is notorious because you can't reload it in runtime, when you change e.g. system
property, and this function fixes that. Param 'require-opts' is a list of require
statement for loading 'env' object and is options since it will introduce 'env' var
in your namespace, the way you'd like. For example, you can use it as:

  (reload-env! '[environ.core :refer [env]])

to introduce it as 'env' or:

  (reload-env! '[environ.core :as ec])

to introduce it as 'ec/env'.

Note that calling this function without arguments will bring 'env' in your namespace."
  ([require-opts]
     (.unbindRoot (ns-resolve 'environ.core 'env))
     (require require-opts :reload))
  ([] (reload-env! '[environ.core :refer [env]])))

(defmacro prog1
  "Evaluate all expressions (like begin), but return result of first expression."
  [& body]
  `(let [ret# ~(first body)]
     ~@(rest body)
     ret#))
