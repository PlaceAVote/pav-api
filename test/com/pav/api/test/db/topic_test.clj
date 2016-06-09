(ns com.pav.api.test.db.topic-test
  "Topic testing code."
  (:use clojure.test)
  (:require [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.string :as s]
            [com.gfredericks.test.chuck.clojure-test :refer [checking]]
            [com.pav.api.db.db :refer [drop-all-tables!]]
            [com.pav.api.db.migrations :refer [migrate!]]
            [com.pav.api.db.user :as u]
            [com.pav.api.db.topic :as t]
            [com.pav.api.test.db.generators :as gg]
            [com.pav.api.test.utils.utils :refer [contains-map? contains-vec? rand-int-range]]))

(defn- clear-db [f]
  (drop-all-tables!)
  (migrate!)
  (when f (f)))

(def topic-gen
  "Generate random topic."
  (gen/not-empty gen/string-alphanumeric))

(use-fixtures :once clear-db)

(deftest topic-test
  (checking "generate some topics" 100
    [topic topic-gen]
    (if-not (t/have-topic? topic)
      (is (number? (t/add-topic topic)))))

  (let [[s e] (t/first-last-ids)]
    (checking "check get-topic-by-id, get-topic and have-topic?" 100
      [id (gen/choose s e)]
      (let [ret (t/get-topic-by-id id)]
        (is (number? (:id ret)))
        (is (string? (:name ret)))
        (is (= (t/get-topic (:name ret)) id))
        (is (t/have-topic? (:name ret))))))

  (let [[s e] (t/first-last-ids)]
    (checking "check get-or-add-topic" 100
      [id (gen/choose s e)
       new topic-gen]
      (let [mp (t/get-topic-by-id id)
            topic (:name mp)]
        (is (= (t/get-or-add-topic topic) (:id mp)))
        (is (number? (t/get-or-add-topic new))))))

  (checking "generate some users and add topic" 100
    [ugen (gen/no-shrink gg/user-gen)
     pgen (gen/no-shrink gg/creds-gen)
     new  topic-gen]
    (when-let [user-id (u/create-user (merge ugen pgen))]
      (is (number? user-id))
      (is (number? (t/add-user-topic user-id new)))
      ;; adding the same topic, which already exists, should return nil
      (is (nil? (t/add-user-topic user-id new)))))

  (let [[us ue] (u/first-last-ids)]
    (checking "add multiple topics to the user" 100
      [random-user-id  (gen/choose us ue)
       new-topics      (gen/not-empty (gen/vector topic-gen))]
      ;; add-user-topics returns nil since it invokes 'doseq'
      (is (nil? (t/add-user-topics random-user-id new-topics)))
      (is (contains-vec?
           (t/get-user-topics random-user-id)
           (map s/capitalize new-topics))))))
