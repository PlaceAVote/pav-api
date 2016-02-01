(ns com.pav.user.api.test.issues.graph-parser-test
  (:use midje.sweet)
  (:require [com.pav.user.api.graph.graph-parser :as g]))

(fact "Given a url, When the page contains Open Graph meta tags, Then Parse and return necessary tags."
  (let [response (g/extract-open-graph "https://medium.com/the-trans-pacific-partnership/here-s-the-deal-the-text-of-the-trans-pacific-partnership-103adc324500#.mn7t24yff")]
    response
    => (contains {:article_title "Here’s the Deal: The Text of the Trans-Pacific Partnership — The Trans-Pacific Partnership"
                  :article_link  "https://medium.com/the-trans-pacific-partnership/here-s-the-deal-the-text-of-the-trans-pacific-partnership-103adc324500"})
    (some nil? (vals response)) => nil))

(fact "Given a url, When the url doesn't exist, Then return nil"
  (g/extract-open-graph "http://dsadsadasdsadasdasdasdas2321321.com") => nil)

(fact "Given a url, When the resource doesn't contain open graph data, Then return nil"
  (g/extract-open-graph "https://clojuredocs.org/") => nil)
