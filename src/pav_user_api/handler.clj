(ns pav-user-api.handler
  (:require [compojure.core :refer [defroutes routes]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.json :refer [wrap-json-body wrap-json-params wrap-json-response]]
            [ring.middleware.file-info :refer [wrap-file-info]]
            [hiccup.middleware :refer [wrap-base-url]]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer :all]
            [pav-user-api.models.user :refer [list-users create user authenticate]]
            [liberator.dev :refer [wrap-trace]]))

(defn init []
  (println "pav-user-api is starting"))

(defn destroy []
  (println "pav-user-api is shutting down"))

(defroutes app-routes
  (GET "/user/:email" [email] (user email))
  (GET "/user" [] list-users)
  (PUT "/user" user create)
  (POST "/user/authenticate" user authenticate)
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (-> (routes app-routes)
      (wrap-json-body {:keywords? true})
      (wrap-json-response)
      (handler/site)
      (wrap-base-url)
      (wrap-trace :header :ui)))
