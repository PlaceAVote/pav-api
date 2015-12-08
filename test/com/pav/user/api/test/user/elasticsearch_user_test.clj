(ns com.pav.user.api.test.user.elasticsearch-user-test
  (:use [midje.sweet])
  (:require [com.pav.user.api.elasticsearch.user :as eu :refer [index-user]]
            [com.pav.user.api.test.utils.utils :refer [flush-user-index
                                                       bootstrap-bills]]
						[clojurewerkz.elastisch.query :as q]
						[clojurewerkz.elastisch.rest :refer [connect]]
						[clojurewerkz.elastisch.rest.document :as esd]
						[clojurewerkz.elastisch.rest.response :as esrsp]))

(def connection (connect))

(defn merge-type-and-fields [hit]
	(merge {:type (:_type hit)} (:_source hit)))

(defn search-for-term [terms]
	(when terms
		(->> (esrsp/hits-from
					 (esd/search connection "congress" "bill"
						 :query (q/terms :keywords terms)
						 :_source [:subject :bill_id :official_title :short_title :popular_title]
						 :sort  {:updated_at "desc"}))
			(mapv merge-type-and-fields)))
	)

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

(against-background [(before :facts (do (flush-user-index)
																				(bootstrap-bills)))]
	(fact "Given a user profile, index user profile"
		(let [user-profile {:user_id "user1" :email "john@pl.com" :first_name "John" :last_name "Rambo"
												:dob     "05/10/1984" :img_url "http://img.com"}
					_ (index-user user-profile)]))

	(fact "Given a collection of topics containing the term Politics, return hr1764 in result set"
		(let [_ (Thread/sleep 1000)
					results (gather-latest-bills-by-subject ["Politics" "Defense"])]
			(count results) => 2
			results => (contains [{:type           "bill"
														 :bill_id        "hr2029-114"
														 :official_title "Making appropriations for military construction, the Department of Veterans Affairs, and related agencies for the fiscal year ending September 30, 2016, and for other purposes."
														 :short_title    "Military Construction and Veterans Affairs and Related Agencies Appropriations Act, 2016"
														 :subject        "Defense"}
														{:type           "bill"
														 :bill_id        "hr1764-114"
														 :official_title "To provide for the designation of the United States Chief Technology Officer."
														 :short_title    "United States Chief Technology Officer Act"
														 :subject        "Politics"}]))))
