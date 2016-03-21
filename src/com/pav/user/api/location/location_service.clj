(ns com.pav.user.api.location.location-service
  (:require [environ.core :refer [env]]
            [clj-http.client :as http]
            [cheshire.core :as ch]
            [clojure.string :as s]))

(def congress-api-key (:sunlight-congress-apikey env))

(defn location-by-zip
  "Retrieve location data from googles api and retrieve congressional district from sunlights congress API."
  [zip]
  (let [search-result (-> (http/get (str "https://maps.googleapis.com/maps/api/geocode/json?address=" zip))
                        :body (ch/parse-string true) :results first)
        lat-lng (get-in search-result [:geometry :location])
        district-info (-> (http/get (str "https://congress.api.sunlightfoundation.com/districts/locate?latitude=" (:lat lat-lng) "&longitude=" (:lng lat-lng) "&apikey=" congress-api-key))
                        :body (ch/parse-string true) :results first)
        formatted_address (:formatted_address search-result)]
    (-> {:address formatted_address :country_code (-> (s/split formatted_address #",") last s/trim)}
      (merge lat-lng district-info))))