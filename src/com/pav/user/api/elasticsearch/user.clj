(ns com.pav.user.api.elasticsearch.user
	(:require [clojurewerkz.elastisch.rest :refer [connect]]
						[clojurewerkz.elastisch.rest.document :as esd]
						[clojurewerkz.elastisch.rest.response :as esrsp]
						[clojurewerkz.elastisch.query :as q]
						[environ.core :refer [env]]))

(def connection (connect (:es-url env)))

(defn index-user [user]
  (esd/put connection "pav" "users" (:user_id user) user))

(defn merge-type-and-fields [hit]
	(merge {:type (:_type hit)} (:_source hit)))

(defn get-bill-info [bill_id]
  (:_source (esd/get connection "congress" "bill" bill_id)))

(defn search-for-term [terms]
	(when terms
		(->> (esrsp/hits-from
					 (esd/search connection "congress" "bill"
						 :query (q/terms :keywords terms)
						 :_source [:subject :bill_id :official_title :short_title :popular_title :summary]
						 :sort  {:updated_at "desc"}))
			(mapv merge-type-and-fields)
      (filterv (fn [{summary :summary}]
                 (not (= summary "No Summary Present...")))))))

(defn to-govtrack-subjects [topic]
	(case topic
		"Religion" "religion"
		"Drugs" "drug"
		"Defense" "defense"
		"Politics" "politics"
		"Gun Rights" "firearms"
		"Technology" "technology"
		"Economics" "Economics"
		"Social Interest" "Social welfare"
		topic))

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

(defn gather-latest-bills-by-subject [topics]
	(when topics
		(let [topics (map to-govtrack-subjects topics)
					bills (->> (search-for-term topics)
									flatten
									(mapv #(update-in % [:subject] to-pav-subjects)))]
			bills)))
