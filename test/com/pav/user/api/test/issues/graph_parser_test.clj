(ns com.pav.user.api.test.issues.graph-parser-test
  (:use midje.sweet)
  (:require [com.pav.user.api.graph.graph-parser :as g]))

(fact "Given a url, When the page contains Open Graph meta tags, Then Parse and return necessary tags."
  (g/extract-open-graph "http://techcrunch.com/")
    => {:article_title "TechCrunch"
        :article_link "http://social.techcrunch.com/"
        :article_img "https://tctechcrunch2011.files.wordpress.com/2014/03/your-ad-here.jpg?w=764&h=400&crop=1"})

(fact "Given a url, When the url doesn't exist, Then return nil"
  (g/extract-open-graph "http://dsadsadasdsadasdasdasdas2321321.com") => nil)

(fact "Given a url, When the resource doesn't contain open graph data, Then return nil"
  (g/extract-open-graph "https://clojuredocs.org/") => nil)
