(ns com.pav.api.test.db.user-test
  "User testing code."
  (:use clojure.test)
  (:require [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [com.gfredericks.test.chuck.clojure-test :refer [checking]]
            [com.pav.api.db.db :refer [drop-all-tables!]]
            [com.pav.api.db.migrations :refer [migrate!]]
            [com.pav.api.db.user :as u]
            [com.pav.api.test.db.generators :as gg]
            [com.pav.api.test.utils.utils :refer [contains-map?]]))

(def nusers
  "Number of users to randomly generate."
  1000)

(defn- clear-db [f]
  (drop-all-tables!)
  (migrate!)
  (when f (f)))

(defn- string-or-nil? [v]
  (or (string? v) (nil? v)))

(use-fixtures :once clear-db)

;; Make sure the first test is always executed, in case you'd like to run some of them. The first
;; test will create all necessary random accounts.
(deftest user-functions-test
  (checking "generate random users" nusers
    ;; do not shrink or test.check will start to generate users with the same
    ;; email address (trying to minimize error) causing db to throw primary key violation
    [ugen (gen/no-shrink gg/user-gen)
     pgen (gen/no-shrink gg/creds-gen)]
    (is (number? (u/create-user (merge pgen ugen)))))

  (testing "size of stored records"
    (is (= (u/user-count) nusers))
    (is (= (u/user-count) (count
                           (doall (u/retrieve-all-user-records))))))

  (let [[s e] (u/first-last-ids)]
    (checking "check get-user-by-id == get-user-by-old-id" nusers
      [id (gen/choose s e)]
      (let [data   (u/get-user-by-id id)
            old_id (:old_user_id data)]
        (is (= data (u/get-user-by-old-id old_id))))))

  (let [[s e] (u/first-last-ids)]
    (checking "check get-user-by-id == get-user-by-email" nusers
      [id (gen/choose s e)]
      (let [data  (u/get-user-by-id id)
            email (:email data)]
        (is (= data (u/get-user-by-email email))))))

  (let [[s e] (u/first-last-ids)]
    (checking "check get-user-by-facebook-id == get-user-by-id" nusers
      [id (gen/choose s e)]
      (if-not (u/has-user-password? id)
        (let [data   (u/get-user-by-id id)
              tokens (u/get-fb-id-and-token id)
              data2  (u/get-user-by-facebook-id (:facebook_id tokens))]
          (is (string? (:facebook_id tokens)))
          (is (string? (:facebook_token tokens)))
          (is (contains-map? data2 data))))))

  (let [[s e] (u/first-last-ids)]
    (checking "check get-user-password, update-user-password and get-fb-id-and-token" nusers
      [id   (gen/choose s e)
       pass (gen/not-empty gen/string-alphanumeric)]
      (is (string-or-nil? (u/get-user-password id)))
      (if (u/has-user-password? id)
        ;; if we do have password
        (do
          ;; set new password
          (is (coll? (u/update-user-password id pass)))
          (is (= pass (u/get-user-password id)))
          (is (= pass (-> id
                          u/get-user-by-id
                          :old_user_id
                          (u/get-user-password true)))))
        ;; no password, must have fb tokens
        (let [tokens  (u/get-fb-id-and-token id)
              tokens2 (-> id u/get-user-by-id :old_user_id (u/get-fb-id-and-token true))]
          (is (string? (:facebook_id tokens)))
          (is (string? (:facebook_token tokens)))
          (is (= tokens tokens2)) )))))
