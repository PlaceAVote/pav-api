(ns com.pav.api.test.db.topic-test
  "Topic testing code."
  (:use midje.sweet
        clojure.test)
  (:require [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [com.pav.api.db.db :refer [drop-all-tables!]]
            [com.pav.api.db.migrations :refer [migrate!]]
            [com.pav.api.db.user :as u]
            [com.pav.api.db.topic :as t]
            [com.pav.api.test.db.generators :as gg]))

(defn- clear-db []
  (drop-all-tables!)
  (migrate!))

(deftest topic-test
  (against-background [(before :contents (clear-db))]
    (fact "generate some topics"
      (tc/quick-check 100
        (prop/for-all [topic (gen/not-empty gen/string-alphanumeric)]
          (number? (t/add-topic topic)))))
    => (just {:result true :num-tests 100 :seed anything})
    
    (fact "check get-topic-by-id, get-topic and have-topic?"
      (let [[s e] (t/first-last-ids)]
        (tc/quick-check 100
          (prop/for-all [id (gen/choose s e)]
            (let [ret (t/get-topic-by-id id)]
              ret => (just {:id number? :name string?})
              (t/get-topic (:name ret)) => id
              (t/have-topic? (:name ret)) => true)))))
    => (just {:result true :num-tests 100 :seed anything})
    
    (fact "check get-or-add-topic"
      (let [[s e] (t/first-last-ids)]
        (tc/quick-check 100
          (prop/for-all [id (gen/choose s e)
                         new (gen/not-empty gen/string-alphanumeric)]
            (let [mp (t/get-topic-by-id id)
                  topic (:name mp)]
              (t/get-or-add-topic topic) => (:id mp)
              (number? (t/get-or-add-topic new)))))))
    => (just {:result true :num-tests 100 :seed anything})
    
    ))
