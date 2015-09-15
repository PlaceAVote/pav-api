(ns pav-user-api.resources.docs
  (:require [liberator.core :refer [resource defresource]]
            [liberator.representation :refer [ring-response]]
            [ring.swagger.swagger2 :as rs]
            [pav-user-api.schema.user :refer [User UserRecord]]))

(def user-docs
  (rs/swagger-json
    {:info {:title "PAV User API"
            :description "An API for registering new users and authenticating existing users"
            :version "0.0.1"}
     :tags [{:name "user"
             :description "User actions"}]
     :paths {"/user" {:put {:summary "Register user"
                            :description "Register user"
                            :tags ["user"]
                            :parameters {:body User}
                            :responses {201 {:schema UserRecord
                                             :description "New User Record Created"}}}}}
     :consumes ["application/json"]
     :produces ["application/json"]
     :swagger "2.0"}))

(defresource swagger-docs
  :available-media-types ["application/json"]
  :handle-ok user-docs)
