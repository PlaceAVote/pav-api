(ns com.pav.user.api.test.votes.votes-api-test
  (:use midje.sweet)
  (:require [com.pav.user.api.test.utils.utils :as u]))

(against-background [(before :facts (do (u/flush-dynamo-tables)
                                        (u/flush-redis)))]
  (facts "Test cases to cover votes API."
    ))
