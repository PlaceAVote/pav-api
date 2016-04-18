(ns com.pav.api.location.location-service
  (:require [environ.core :refer [env]]
            [clojure.string :as s]
            [com.pav.api.utils.utils :as u]
            [clojure.core.memoize :as memo]))

(def congress-api-key (:sunlight-congress-apikey env))
(def google-geo-apikey (:google-geolocation-apikey env))

(defn- to-address-country-code
  "Take string with address and country code and returns a map with
:address and :country_code keys, where country code is extracted."
  [address]
  (when address
    (let [address (s/trim address)]
      {:address address
       :country_code (-> address (s/split #"\s*,\s*") last)})))

(defn- location-by-zip
  "Retrieve location data from googles api and retrieve congressional district from sunlights congress API."
  [zip]
  (let [search-result (u/http-to-json (str "maps.googleapis.com/maps/api/geocode/json?key=" google-geo-apikey "&address=" zip))
        lat-lng (get-in search-result [:geometry :location])
        district-info (u/http-to-json-sunlightfoundation-api
                       (format "/districts/locate?latitude=%s&longitude=%s&apikey=%s"
                               (:lat lat-lng) (:lng lat-lng) congress-api-key))]
    (merge
     (-> search-result :formatted_address to-address-country-code)
     lat-lng
     district-info)))

(def retrieve-location-by-zip
  "Cache the retrieval of zipcode in order to limit us exceeding our quota for common zipcodes."
  (memo/ttl location-by-zip :ttl/threshold 86400))
