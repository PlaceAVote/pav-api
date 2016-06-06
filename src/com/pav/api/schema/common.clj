(ns com.pav.api.schema.common
  (:require [schema.core :as s])
  (import (java.util Locale)))

(defn retrieve-iso3-country-code [country]
  (-> (Locale. "" country)
    (.getISO3Country)))

(def iso3-codes (into #{} (map retrieve-iso3-country-code (Locale/getISOCountries))))
(def pwd-schema (s/both (s/pred (complement empty?)) s/Str #"^$|^\S.{4,}\S$"))
(def email-schema (s/both (s/pred (complement empty?)) #"^[^@]+@[^@\\.]+[\\.].+"))
(def dob-schema #"^-?\d{12}$")
(def county-code-schema (s/both s/Str (s/pred (complement empty?)) (s/pred #(contains? iso3-codes %))))
(def gender-schema (s/both s/Str (s/pred (complement empty?)) (s/enum "male" "female" "they")))
(def str-schema (s/both (s/pred (complement empty?)) s/Str))
(def zip-schema #"^[0-9]{5}$")