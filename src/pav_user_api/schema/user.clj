(ns pav-user-api.schema.user
  (:require [schema.core :as s]
            [clojure.tools.logging :as log]
            [clojure.tools.logging :as log])
  (import (java.util Locale)))

(defn retrieve-iso3-country-code [country]
  (-> (Locale. "" country)
      (.getISO3Country)))

(def iso3-codes (into #{} (map retrieve-iso3-country-code (Locale/getISOCountries))))

(def User
  {:email        (s/both (s/pred (complement empty?)) #"^[^@]+@[^@\\.]+[\\.].+")
   :password     (s/both (s/pred (complement empty?)) s/Str)
   :first_name   s/Str
   :last_name    s/Str
   :dob          #"^[0-3]?[0-9].[0-3]?[0-9].(?:[0-9]{2})?[0-9]{2}$"
   :country_code (s/both s/Str
                         (s/pred (complement empty?))
                         (s/pred #(contains? iso3-codes %)))
   :topics [s/Str]})

(def FacebookUser
  {:email        (s/both (s/pred (complement empty?)) #"^[^@]+@[^@\\.]+[\\.].+")
   :first_name   s/Str
   :last_name    s/Str
   :dob          #"^[0-3]?[0-9].[0-3]?[0-9].(?:[0-9]{2})?[0-9]{2}$"
   :country_code (s/both s/Str
                         (s/pred (complement empty?))
                         (s/pred #(contains? iso3-codes %)))
   :img_url s/Str
   :topics [s/Str]
   :token s/Str})

(def UserRecord
  {:email        (s/both (s/pred (complement empty?)) #"^[^@]+@[^@\\.]+[\\.].+")
   :first_name   s/Str
   :last_name    s/Str
   :dob          #"^[0-3]?[0-9].[0-3]?[0-9].(?:[0-9]{2})?[0-9]{2}$"
   :country_code (s/both s/Str
                         (s/pred (complement empty?))
                         (s/pred #(contains? iso3-codes %)))
   :topics [s/Str]
   :token s/Str})

(def UserLogin
  {:email    (s/both (s/pred (complement empty?)) #"^[^@]+@[^@\\.]+[\\.].+")
   :password (s/both (s/pred (complement empty?)) s/Str)})

(def FacebookLogin
  {:email    (s/both (s/pred (complement empty?)) #"^[^@]+@[^@\\.]+[\\.].+")
   :token    s/Str})

(defn validate [user origin]
  (case origin
    :pav (s/check User user)
    :facebook (s/check FacebookUser user)))

(defn validate-login [user origin]
  (case origin
    :pav (s/check UserLogin user)
    :facebook (s/check FacebookLogin user)))

(defn find-suitable-error [[k _]]
  (cond (= :email k) {k "A valid email address is a required"}
        (= :password k) {k "Password is a required field"}
        (= :country_code k) {k "Country Code is a required field.  Please Specify Country Code"}
        (= :first_name k) {k "First Name is a required field"}
        (= :last_name k) {k "Last Name is a required field"}
        (= :dob k) {k "Date of birth is a required field"}
        (= :topics k) {k "Please specify a list of topics."}
        (= :token k) {k "A token is required for social media registerations and logins"}
        (= :img_url k) {k "A IMG URL is required for social media registerations and logins"}))

(defn construct-error-msg [errors]
  (log/error (str "An Error has occured " errors))
  {:errors (map find-suitable-error errors)})