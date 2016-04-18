(ns com.pav.api.test.legislator.legislator-api-test
  (:use midje.sweet)
  (:require [com.pav.api.test.utils.utils :as u :refer [pav-req]]))

(against-background [(before :contents (do (u/flush-es-indexes)
                                        (u/bootstrap-legislators)))]
  (facts "Test Cases covering API related calls for Legislators"

    (fact "Retrieving Legislator information by thomas id, When no Authentication Token is present, Then return legislator"
      (let [{status :status body :body} (pav-req :get "/legislators/01751")]
        status => 200
        body => u/test-legislator))))
