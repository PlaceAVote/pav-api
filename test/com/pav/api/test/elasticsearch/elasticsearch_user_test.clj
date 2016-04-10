(ns com.pav.api.test.elasticsearch.elasticsearch-user-test
  (:use [midje.sweet])
  (:require [com.pav.api.elasticsearch.user :as eu :refer [index-user
																																gather-latest-bills-by-subject]]
            [com.pav.api.test.utils.utils :refer [flush-es-indexes
                                                       bootstrap-bills-and-metadata]]
						[clojurewerkz.elastisch.query :as q]
						[clojurewerkz.elastisch.rest :refer [connect]]))


(against-background [(before :facts (do (flush-es-indexes)
																				(bootstrap-bills-and-metadata)))]
	(fact "Given a user profile, index user profile"
		(let [user-profile {:user_id "user1" :email "john@pl.com" :first_name "John" :last_name "Rambo"
												:dob     "05/10/1984" :img_url "http://img.com"}
					_ (index-user user-profile)]))

	(fact "Given a collection of topics containing the term Healthcare, return hr2 in result set"
		(let [_ (Thread/sleep 3000)
					results (gather-latest-bills-by-subject ["Healthcare"])]
			(count results) => 1
			(first results) => (contains
													 {:type       "bill"
                            :bill_id    "hr2-114"
                            :short_title "Medicare Access and CHIP Reauthorization Act of 2015"
                            :subject    "Healthcare"})))

  (fact "Given a collection of topics containing the term Gun Rights, When the only bill has no summary, Then return no results"
    (let [_ (Thread/sleep 3000)
          results (gather-latest-bills-by-subject ["Gun Rights"])]
      (empty? results) => true)))
