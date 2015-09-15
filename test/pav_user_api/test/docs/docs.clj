(ns pav-user-api.test.docs.docs
  (:use midje.sweet)
  (:require [ring.mock.request :refer [request body content-type header]]
            [pav-user-api.handler :refer [app]]
            [pav-user-api.schema.user :refer [User UserRecord]]
            [cheshire.core :as ch]))

(facts ""
  (future-fact "Return user api swagger.json"
    (let [response (app (request :get "/docs"))]
      (println (:body response))
      (:status response) => 200
      (ch/parse-string (:body response) true) => {:info {:title "PAV User API"
                                                         :description "An API for registering new users and authenticating existing users"
                                                         :version "0.0.1"}
                                                  :tags [{:name "user"
                                                          :description "User actions"}]
                                                  :paths {"/user" {:put {:summary "Register user"
                                                                         :description "Register user"
                                                                         :tags ["user"]
                                                                         :parameters {:body User}}}}
                                                  :responses {201 {:schema UserRecord
                                                                   :description "New User Record Created"}}
                                                  :produces ["application/json"]
                                                  :consumes ["application/json"]
                                                  :swagger "2.0"
                                                  })))
