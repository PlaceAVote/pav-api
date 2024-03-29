(ns com.pav.api.db.topic
  "Functions for dealing with topics. All topics are capitalized in database to
prevent duplicates."
  (:require [com.pav.api.db.db :as db]
            [com.pav.api.db.tables :as t]
            [com.pav.api.db.common :refer [unclobify extract-value]]
            [com.pav.api.utils.utils :refer [sstr]]
            [clojure.java.jdbc :as sql]
            [clojure.tools.logging :as log]
            [clojure.string :as s]))

(defn get-topic
  "Return topic ID if is present or nil if not."
  [t]
  (-> (sql/query db/db [(sstr "SELECT id FROM " t/topics-table " WHERE name = ? LIMIT 1")
                        (s/capitalize t)]
                 {:row-fn unclobify})
      first
      :id))

(defn get-topic-by-id
  "Return topic map by given ID."
  [id]
  (first
   (sql/query db/db [(sstr "SELECT * FROM " t/topics-table " WHERE id = ? LIMIT 1") id]
              {:row-fn unclobify})))

(defn have-topic?
  "Return true/false if topic is present."
  [t]
  (-> t get-topic boolean))

(defn add-topic
  "Register new topic and returns topic ID. Does not check if topic exists, hence if does
exists, database constraints exception will be thrown."
  [t]
  (sql/with-db-transaction [d db/db]
    (extract-value
     (sql/insert! d t/topics-table {:name (s/capitalize t)}))))

(defn get-or-add-topic
  "Add new topic and return topic ID. If topic is already present, return it's ID."
  [t]
  (if-let [id (get-topic t)]
    id
    (add-topic t)))

(defn get-user-topics
  "Returns all user topics."
  [id]
  (some->> (sql/query db/db [(sstr "SELECT t.name FROM " t/user-topics-table " AS u "
                                   "JOIN " t/topics-table " AS t "
                                   "ON u.topic_id = t.id "
                                   "WHERE u.user_id = ?") id])
           seq
           (map :name)))

(defn add-user-topic
  "Add new topic to the given user. Does nothing if topic asigned to this user already exists.
Optional argument is transaction, so it can run in a single transaction that does more that topic
insert."
  ([transaction id t]
     (let [insert-fn #(sql/insert! % t/user-topics-table {:user_id id :topic_id (get-or-add-topic t)})]
       (if-not (some #{t} (get-user-topics id))
         (if-not transaction
           (sql/with-db-transaction [d db/db]
             (insert-fn d))
           (insert-fn transaction))
         (log/warnf "Topic '%s' already assigned to user with ID=%d" t id))))
  ([id t] (add-user-topic nil id t)))

(defn add-user-topics
  "Add multiple topics to given user."
  ([transaction id topics]
     (doseq [t topics]
       (add-user-topic transaction id t)))
  ([id topics] (add-user-topics nil id topics)))

(defn first-last-ids
  "Return first and last ID's. Mainly as helper for testing."
  []
  (let [getter (fn [asc?]
                 (extract-value
                  (sql/query db/db [(str "SELECT id FROM " t/topics-table " ORDER BY id"
                                          (if asc?
                                            " ASC "
                                            " DESC ")
                                          "LIMIT 1")])))]
    [(getter true) (getter false)]))
