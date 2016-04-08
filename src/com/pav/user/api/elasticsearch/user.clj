(ns com.pav.user.api.elasticsearch.user
  (:require [clojurewerkz.elastisch.rest :refer [connect]]
            [clojurewerkz.elastisch.rest.document :as esd]
            [clojurewerkz.elastisch.rest.response :as esrsp]
            [clojurewerkz.elastisch.query :as q]
            [environ.core :refer [env]]
            [taoensso.truss :refer [have]]
            [clojure.tools.logging :as log]))

(def connection (connect (:es-url env)))

(defn index-user [user]
  (esd/put connection "pav" "users" (:user_id user) user {:refresh true}))

(defn merge-type-and-fields [hit]
  (merge {:type (:_type hit)} (:_source hit)))

(defn to-pav-subjects [subject]
  (case subject
    "Arts, culture, religion" "Religion"
    "Crime and law Enforcement" "Drugs"
    "Armed forces and national security" "Defense"
    "Government operations and politics" "Politics"
    "Crime and law enforcement" "Gun Rights"
    "Science, technology, communications" "Technology"
    "Economics and public finance" "Economics"
    "Social welfare" "Social Interest"
    subject))

(defn assign-pav-subject [{:keys [type subject] :as hit}]
  (if (= type :bill)
    (update-in hit [subject] to-pav-subjects)
    hit))

(defn get-bill-info [bill_id]
  (have string? bill_id)
  (:_source (esd/get connection "congress" "bill" bill_id)))

(defn retrieve-bill-metadata-for-topics [topics]
  (when topics
    (->>
      (esrsp/hits-from
        (esd/search connection "congress" "billmeta"
          :query (q/terms :pav_topic (map clojure.string/lower-case topics))))
      (map :_source))))

(defn search-for-topic
  "Match a users topic selections against pav_topic field on billmeta type"
  [topics]
  (try
    (map (fn [{:keys [bill_id] :as meta}]
           (merge meta {:type "bill"}
             (select-keys (get-bill-info bill_id)
               [:subject :bill_id :official_title :short_title :popular_title :summary])))
      (retrieve-bill-metadata-for-topics topics))
    (catch Exception e (log/error "Error retrieving bills when populating users feed " e))))

(defn search-for-term [term]
  "Search for term across bills and placeavote users."
  (->> (esrsp/hits-from (esd/search connection ["congress" "pav"] ["users" "bill"]
                          :query {:multi_match {:query term
                                                :type "best_fields"
                                                :fields ["first_name^2" "last_name^2"
                                                         "keywords" "official_title" "short_title" "popular_title"
                                                         "bill_id" "summary"]
                                                :minimum_should_match "80%"}}
                          :from 0 :size 10
                          :filter {:not {:term {:public false}}}
                          :_source [:first_name :last_name :user_id :img_url
                                    :bill_id :official_title :short_title :popular_title :subject]))
    (mapv merge-type-and-fields)
    (mapv assign-pav-subject)))

(defn gather-latest-bills-by-subject [topics]
	(when topics
		(let [bills (->>
                  (search-for-topic topics)
                  (mapv (fn [{:keys [pav_topic] :as bill}]
                          (if pav_topic
                            (assoc bill :subject pav_topic)
                            (update-in bill [:subject] to-pav-subjects)))))]
			bills)))

(defn get-legislator [thomas]
  (-> (esd/get connection "congress" "legislator" thomas)
    (get-in [:_source])))

(defn get-bill [bill_id]
  (->>
    (esd/multi-get connection "congress" [{:_type "bill" :_id bill_id} {:_type "billmeta" :_id bill_id}])
    (map :_source) (reduce merge)))

(defn get-bills-metadata
  "Retrieve metadata for a collection of bills."
  [bills]
  (->>
    (map #(assoc {} :_id (:bill_id %)) bills)
    (esd/multi-get connection "congress" "billmeta")
    (map :_source)))
