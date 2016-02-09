(ns com.pav.user.api.test.elasticsearch.elasticsearch-user-test
  (:use [midje.sweet])
  (:require [com.pav.user.api.elasticsearch.user :as eu :refer [index-user
																																gather-latest-bills-by-subject]]
            [com.pav.user.api.test.utils.utils :refer [flush-es-indexes
                                                       bootstrap-bills]]
						[clojurewerkz.elastisch.query :as q]
						[clojurewerkz.elastisch.rest :refer [connect]]))


(against-background [(before :facts (do (flush-es-indexes)
																				(bootstrap-bills)))]
	(fact "Given a user profile, index user profile"
		(let [user-profile {:user_id "user1" :email "john@pl.com" :first_name "John" :last_name "Rambo"
												:dob     "05/10/1984" :img_url "http://img.com"}
					_ (index-user user-profile)]))

	(fact "Given a collection of topics containing the term Politics & Defense, return hr1764 and hr2029 in result set"
		(let [_ (Thread/sleep 3000)
					results (gather-latest-bills-by-subject ["Politics" "Defense"])]
			(count results) => 2
			results => (contains [{:type           "bill"
														 :bill_id        "hr2029-114"
														 :official_title "Making appropriations for military construction, the Department of Veterans Affairs, and related agencies for the fiscal year ending September 30, 2016, and for other purposes."
														 :short_title    "Military Construction and Veterans Affairs and Related Agencies Appropriations Act, 2016"
														 :subject        "Defense"
														 :summary 			 "Highlights:<br /><br /> The Military Construction and Veterans Affairs and Related Agencies Appropriations Act, 2016 provides FY2016 appropriations to the Department of Defense (DOD) for military construction, military family housing, the U.S. share of the North Atlantic Treaty Organization Security Investment Program, and base closures and realignments."}
														{:type           "bill"
														 :bill_id        "hr1764-114"
														 :official_title "To provide for the designation of the United States Chief Technology Officer."
														 :short_title    "United States Chief Technology Officer Act"
														 :subject        "Politics"
														 :summary				 "United States Chief Technology Officer Act<br /><br />Amends the National Science and Technology Policy, Organization, and Priorities Act of 1976 to authorize the President to appoint a United States Chief Technology Officer whose duties shall include advising the President and the Director of the Office of Science and Technology Policy on federal information systems, technology, data, and innovation policies and initiatives."}])))

  (fact "Given a collection of topics containing the term Gun Rights, When the only bill has no summary, Then return no results"
    (let [_ (Thread/sleep 3000)
          results (gather-latest-bills-by-subject ["Gun Rights"])]
      (empty? results) => true)))
