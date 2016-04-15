(ns com.pav.api.location.location-service
  (:require [environ.core :refer [env]]
            [clj-http.client :as http]
            [cheshire.core :as ch]
            [clojure.string :as s]))

(def congress-api-key (:sunlight-congress-apikey env))

(defn- is-java7-or-6?
  "Returns true if we are running on Java 7 or 6 (7 is first checks, since it is highly a chance we will hit it).
This is used for SSL workaround for sunlightfoundation.com site."
  []
  (let [version (System/getProperty "java.version")]
     (or (.startsWith version "1.7")
         (.startsWith version "1.6"))))

(defn- http-to-json
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

(defn- to-address-country-code
  "Take string with address and country code and returns a map with
:address and :country_code keys, where country code is extracted."
  [address]
  (when address
    (let [address (s/trim address)]
      {:address address
       :country_code (-> address (s/split #"\s*,\s*") last)})))

(defn location-by-zip
  "Retrieve location data from googles api and retrieve congressional district from sunlights congress API."
  [zip]
  (let [search-result (http-to-json (str "maps.googleapis.com/maps/api/geocode/json?address=" zip))
        lat-lng (get-in search-result [:geometry :location])
        district-info (http-to-json (format "congress.api.sunlightfoundation.com/districts/locate?latitude=%s&longitude=%s&apikey=%s"
                                            (:lat lat-lng)
                                            (:lng lat-lng)
                                            congress-api-key)
                                    (is-java7-or-6?))]
    (merge
     (-> search-result :formatted_address to-address-country-code)
     lat-lng
     district-info)))
