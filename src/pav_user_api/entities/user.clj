(ns pav-user-api.entities.user
  (:use korma.core)
  (:require [pav-user-api.database.database :refer [user_db]]
            [clojure.string :refer [split]]))

(declare users user-token)

(defentity users
           (table :user_info)
           (database user_db)
           (prepare (fn [{topics :topics :as v}]
                      (if topics
                        (assoc v :topics (apply str (interpose "," topics)))
                        v)))
           (transform (fn [{topics :topics :as v}]
                        (if topics
                          (assoc v :topics (split topics #","))
                          v))))

(defentity user-token
           (table :user_token)
           (database user_db))

(defn get-all-users []
  (select users))

(defn get-user [email]
  (select users
          (where {:email email})))

(defn create-user [user]
  (insert users
          (values user)))

(defn create-user-with-token [user token]
  (let [{id :generated_key} (create-user user)
        new-token {:user_id id :token (get-in token [:token])}]
    (insert user-token
            (values new-token))))

(defn get-user-credientials [email]
  (first (-> (select* "user_info")
             (fields :email :password)
             (where {:email email})
             (exec))))
