(ns pav-user-api.schema.user
  (:require [schema.core :as s]))

(def User
  {:email s/Str
   :password s/Str})

(defn validate [user]
  (s/check User user))

(defn find-suitable-error [[k v]]
  (cond (= :email k) {k "Email address is a required field"}
        (= :password k) {k "Password is a required field"}))

(defn construct-error-msg [errors]
  {:errors (map find-suitable-error errors)})