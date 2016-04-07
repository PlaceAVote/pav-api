(ns com.pav.user.api.test.legislator.legislator-api-test
  (:use midje.sweet)
  (:require [com.pav.user.api.test.utils.utils :as u :refer [pav-req]]
            [cheshire.core :as ch]))

(against-background [(before :facts (do (u/flush-es-indexes)
                                        (u/bootstrap-legislators)))]
  (facts "Test Cases covering API related calls for Legislators"

    (fact "Retrieving Legislator information by thomas id, When no Authentication Token is present, Then return legislator"
      (let [{status :status body :body} (pav-req :get "/legislators/01751")
            response (ch/parse-string body true)]
        status => 200
        response => u/test-legislator))))
