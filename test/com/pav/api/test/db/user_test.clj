(ns com.pav.api.test.db.user-test
  "User testing code."
  (:use midje.sweet
        clojure.test)
  (:require [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [com.pav.api.db.db :refer [drop-all-tables!]]
            [com.pav.api.db.migrations :refer [migrate!]]
            [com.pav.api.db.user :as u]
            [com.pav.api.test.db.generators :as gg]))

(def nusers
  "Number of users to randomly generate."
  1000)

(defn- clear-db []
  (drop-all-tables!)
  (migrate!))

(defn- string-or-nil? [v]
  (or (string? v) (nil? v)))

;; Make sure the first test is always executed, in case you'd like to run some of them. The first
;; test will create all necessary random accounts.
(deftest user-functions-test
  (against-background [(before :contents (clear-db))]
    (fact "generate random users"
      (tc/quick-check nusers
        ;; do not shrink or test.check will start to generate users with the same
        ;; email address (trying to minimize error) causing db to throw primary key violation
        (prop/for-all [ugen (gen/no-shrink gg/user-gen)
                       pgen (gen/no-shrink gg/creds-gen)]
            (number? (u/create-user (merge pgen ugen)))))
    => (just {:result true :num-tests nusers :seed anything}))

    (fact "size of stored records"
      (u/user-count) => nusers
      (u/user-count) => (count 
                         (doall (u/retrieve-all-user-records))))
    
    (fact "check get-user-by-id == get-user-by-old-id"
      (let [[s e] (u/first-last-ids)]
        (tc/quick-check 100
          (prop/for-all [id (gen/choose s e)]
            (let [data   (u/get-user-by-id id)
                  old_id (:old_user_id data)]
              (u/get-user-by-old-id old_id) => data)))))
    => (just {:result true :num-tests 100 :seed anything})
    
    (fact "check get-user-by-id == get-user-by-email"
      (let [[s e] (u/first-last-ids)]
        (tc/quick-check 100
          (prop/for-all [id (gen/choose s e)]
            (let [data  (u/get-user-by-id id)
                  email (:email data)]
              (u/get-user-by-email email) => data)))))
    => (just {:result true :num-tests 100 :seed anything})
    
    (fact "check get-user-by-facebook-id == get-user-by-id"
      (let [[s e] (u/first-last-ids)]
        (tc/quick-check 100
          (prop/for-all [id (gen/choose s e)]
            (when-not (u/has-user-password? id)
              (let [data   (u/get-user-by-id id)
                    tokens (u/get-fb-id-and-token id)
                    data2  (u/get-user-by-facebook-id (:facebook_id tokens))]
                tokens => (just {:facebook_id string?, :facebook_token string?})
                ;; (get-user-profile-by-facebook-id) will do JOIN, returning both user
                ;; details and facebook tokens
                data2 => (contains data) ))))))
    => (just {:result true :num-tests 100 :seed anything})

    (fact "check get-user-password, update-user-password and get-fb-id-and-token"
     (let [[s e] (u/first-last-ids)]
       (tc/quick-check 100
         (prop/for-all [id   (gen/choose s e)
                        pass (gen/not-empty gen/string-alphanumeric)]
           (string-or-nil? (u/get-user-password id)) => true
           (if (u/has-user-password? id)
             ;; if we do have password
             (do
               ;; set new password
               (coll? (u/update-user-password id pass)) => true
               (u/get-user-password id) => pass
               (-> id u/get-user-by-id :old_user_id (u/get-user-password true)) => pass)

             ;; no password, mush have fb tokens
             (let [tokens  (u/get-fb-id-and-token id)
                   tokens2 (-> id u/get-user-by-id :old_user_id (u/get-fb-id-and-token true))]
               tokens => (just {:facebook_id string?, :facebook_token string?})
               tokens => tokens2))))))
    => (just {:result true :num-tests 100 :seed anything})
 ))
