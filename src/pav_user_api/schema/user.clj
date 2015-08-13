(ns pav-user-api.schema.user
  (:require [schema.core :as s]
            [schema.utils :as su]))

(def User
  {:email    (s/both (s/pred (complement empty?)) #"^[^@]+@[^@\\.]+[\\.].+")
   :password (s/both (s/pred (complement empty?)) s/Str)})

(defn validate [user]
  (s/check User user))

(defn find-suitable-error [[k v]]
  (cond (= :email k) {k "A valid email address is a required"}
        (= :password k) {k "Password is a required field"}))

(defn construct-error-msg [errors]
  {:errors (map find-suitable-error errors)})