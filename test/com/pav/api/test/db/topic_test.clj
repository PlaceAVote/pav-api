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

(def topic-gen
  "Generate random topic."
  (gen/not-empty gen/string-alphanumeric))

(deftest topic-test
  (against-background [(before :contents (clear-db))]
    (fact "generate some topics"
      (tc/quick-check 100
        (prop/for-all [topic topic-gen]
          (if-not (t/have-topic? topic)
            (number? (t/add-topic topic))
            true)))
      => (just {:result true :num-tests 100 :seed anything}))

    (fact "check get-topic-by-id, get-topic and have-topic?"
      (let [[s e] (t/first-last-ids)]
        (tc/quick-check 100
          (prop/for-all [id (gen/choose s e)]
            (let [ret (t/get-topic-by-id id)]
              ret => (just {:id number? :name string?})
              (t/get-topic (:name ret)) => id
              (t/have-topic? (:name ret)) => true))))
      => (just {:result true :num-tests 100 :seed anything}))

    (fact "check get-or-add-topic"
      (let [[s e] (t/first-last-ids)]
        (tc/quick-check 100
          (prop/for-all [id (gen/choose s e)
                         new topic-gen]
            (let [mp (t/get-topic-by-id id)
                  topic (:name mp)]
              (t/get-or-add-topic topic) => (:id mp)
              (number? (t/get-or-add-topic new))))))
      => (just {:result true :num-tests 100 :seed anything}))

    (fact "generate some users and add topic"
      (tc/quick-check 100
        (prop/for-all [ugen (gen/no-shrink gg/user-gen)
                       pgen (gen/no-shrink gg/creds-gen)
                       new  topic-gen]
          (let [user-id (u/create-user (merge ugen pgen))]
            user-id => number?
            (t/add-user-topic user-id new) => map?)))
      => (just {:result true :num-tests 100 :seed anything}))

    (fact "add multiple topics to the user"
      (let [[ts te] (t/first-last-ids)
            [us ue] (u/first-last-ids)]
        (tc/quick-check 100
          (prop/for-all [random-topic-id (gen/choose ts te)
                         random-user-id  (gen/choose us ue)
                         new-topics      (gen/vector topic-gen)]
            ;; add-user-topics returns nil since it invokes 'doseq'
            (nil? (t/add-user-topics random-user-id new-topics)) => true
            ;; add it twice to check we do not have duplicates here
            (t/add-user-topic random-user-id random-topic-id)
            (t/add-user-topic random-user-id random-topic-id)
            (let [all-topics (t/get-user-topics random-user-id)]
              all-topics => (contains new-topics)
              (count (filter (partial = random-topic-id) all-topics)) => 1))))
      => (just {:result true :num-tests 100 :seed anything}))
    ))
