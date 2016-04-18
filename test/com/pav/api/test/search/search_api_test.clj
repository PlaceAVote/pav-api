(ns com.pav.api.test.search.search-api-test
  (:use midje.sweet)
  (:require [com.pav.api.test.utils.utils :as u :refer [pav-req]]
            [com.pav.api.elasticsearch.user :as eu]
            [cheshire.core :as c]))

(def user1 {:first_name "John" :last_name "Rambo" :user_id "10101" :img_url "http://img.1.com" :public true})
(def user2 {:first_name "Paul" :last_name "Rambo" :user_id "10201" :img_url "http://img.2.com" :public true})
(def user3 {:first_name "Jane" :last_name "Shaheen" :user_id "10301" :img_url "http://img.3.com" :public true})
(def user4 {:first_name "Johnny" :last_name "Five" :user_id "10401" :img_url "http://img.4.com" :public false})

(def test-users [user1 user2 user3 user4])

(defn bootstrap-users []
  (doseq [user test-users]
    (eu/index-user user)))

(against-background [(before :facts (do
                                      (u/flush-es-indexes)
                                      (u/bootstrap-bills-and-metadata)
                                      (bootstrap-users)))]
  (facts "Test cases for search API Endpoints"

    (fact "Given a term, when that term matches a users first_name, Then return that users record"
      (let [{:keys [status body]} (pav-req :get "/search?term=john")]
        status => 200
        (first body) => (-> (assoc user1 :type "users") (dissoc :public))))

    (fact "Given a term, When the term matches a users first_name and that user is private, Then return empty search results"
      (let [{:keys [status body]} (pav-req :get "/search?term=johnny")]
        status => 200
        body => '()))

    (fact "Given a term, When the term matches the last_name of two users, Then return those users in the search results"
      (let [{:keys [status body]} (pav-req :get "/search?term=rambo")]
        status => 200
        body => (contains [(-> (assoc user1 :type "users") (dissoc :public))
                           (-> (assoc user2 :type "users") (dissoc :public))] :in-any-order)))


    (fact "Given term, When term matches sponsors last name, Then return empty result set"
      (let [{:keys [status body]} (pav-req :get "/search?term=Burgess")]
        status => 200
        body => '()))

    (fact "Given term, When term matches a user and sponsors last name, Then return only one user record."
      (let [{:keys [status body]} (pav-req :get "/search?term=Shaheen")]
        status => 200
        (first body) => (-> (assoc user3 :type "users") (dissoc :public))))

    (fact "Given term, When term appears as a keyword, Then return only one bill record"
      (let [{:keys [status body]} (pav-req :get "/search?term=foreign")]
        status => 200
        (first body) => (contains {:bill_id "s25-114" :type "bill"})))

    (fact "Given term, When that term appears in both bills short_titles,
           Then both both bills, s25 should have priority because it contains both terms"
      (let [{:keys [status body]} (pav-req :get "/search?term=growth%20act")]
        status => 200
        (count body) => 4
        (first body) => (contains {:bill_id "s25-114" :type "bill"})))

    (fact "Given term, When that term matches bill_id, Then return that bill."
      (let [{:keys [status body]} (pav-req :get "/search?term=hr2")]
        status => 200
        (count body) => 1
        (first body) => (contains {:bill_id "hr2-114" :type "bill"})))

    (fact "Given terms, When those terms match part of the summary field, Then return that bill with the featured bill title."
      (let [{:keys [status body]} (pav-req :get "/search?term=Medicare%20Payment%20Advisory%20Commission")]
        status => 200
        (count body) => 1
        (first body) => (contains {:bill_id "hr2-114" :type "bill" :featured_bill_title "hr2 bill title"})))))
