(ns com.pav.api.test.opengraph.opengraph-api-test
  (:use midje.sweet)
  (:require [com.pav.api.test.utils.utils :refer [pav-req
                                                  new-pav-user
                                                  flush-dynamo-tables]]))
(against-background [(before :facts (flush-dynamo-tables))]
  (facts "Opengraph API Tests"
    (fact "Given a valid URL, then return correct OpenGraph data"
      (let [{body :body} (pav-req :put "/user" (new-pav-user))
            {token :token} body
            result (pav-req :get "/opengraph/scrape?link=http://time.com/3319278/isis-isil-twitter" token {})]
        (:body result) => (just {:article_link  "http://time.com/3319278/isis-isil-twitter/"
                                 :article_img   "https://timedotcom.files.wordpress.com/2014/09/e9630acbf2ac43c888eac7253e729608-0.jpg?quality=75&strip=color&w=1012"
                                 :article_title "Why Terrorists Love Twitter"})))

    (facts "Given an invalid URL, Then return correct OpenGraph data"
      (let [{body :body} (pav-req :put "/user" (new-pav-user))
            {token :token} body
            result (pav-req :get "/opengraph/scrape?link=http://garbage.com" token {})]
        (:body result) => (just {:article_link  "http://garbage.com"
                                 :article_img   nil
                                 :article_title nil})))

    (facts "Given any URL, When using an invalid token Then return 401"
      (let [result (pav-req :get "/opengraph/scrape?link=http://garbage.com" "crap" {})]
        (:status result) => 401))))


