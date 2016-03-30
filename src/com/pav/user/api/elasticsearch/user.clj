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
  (esd/put connection "pav" "users" (:user_id user) user))

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
		(let [bills (->>
                  (search-for-topic topics)
									(mapv #(update-in % [:subject] to-pav-subjects)))]
			bills)))
