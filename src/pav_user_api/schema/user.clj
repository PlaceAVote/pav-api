(ns pav-user-api.schema.user
  (:require [schema.core :as s]
            [clojure.tools.logging :as log]
            [clojure.tools.logging :as log]))

(def User
  {:email    (s/both (s/pred (complement empty?)) #"^[^@]+@[^@\\.]+[\\.].+")
   :password (s/both (s/pred (complement empty?)) s/Str)
   :first_name s/Str
   :last_name s/Str
   :country-code s/Int})

(def UserLogin
  {:email    (s/both (s/pred (complement empty?)) #"^[^@]+@[^@\\.]+[\\.].+")
   :password (s/both (s/pred (complement empty?)) s/Str)})

(defn validate [user]
  (s/check User user))

(defn validate-login [user]
  (s/check UserLogin user))

(defn find-suitable-error [[k v]]
  (cond (= :email k) {k "A valid email address is a required"}
        (= :password k) {k "Password is a required field"}
        (= :country-code k) {k "Country Code is a required field.  Please Specify Country Code"}
        (= :first_name k) {k "First Name is a required field"}
        (= :last_name k) {k "Last Name is a required field"}))

(defn construct-error-msg [errors]
  (log/error (str "An Error has occured " errors))
  {:errors (map find-suitable-error errors)})