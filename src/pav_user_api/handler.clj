(ns pav-user-api.handler
  (:require [compojure.core :refer [defroutes routes]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.json :refer [wrap-json-body wrap-json-params wrap-json-response]]
            [ring.middleware.file-info :refer [wrap-file-info]]
            [ring.middleware.cors :refer [wrap-cors]]
            [hiccup.middleware :refer [wrap-base-url]]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer :all]
            [pav-user-api.models.user :refer [list-users create user authenticate]]
            [pav-user-api.authentication.authentication :refer [token-handler]]
            [liberator.dev :refer [wrap-trace]]
            [buddy.auth.middleware :refer [wrap-authentication]]
            [environ.core :refer [env]]))

(defn init []
  (println "pav-user-api is starting"))

(defn destroy []
  (println "pav-user-api is shutting down"))

(defroutes app-routes
  (GET "/user/:email" [email] (user email))
  (GET "/user" [] list-users)
  (PUT "/user" _ create)
  (POST "/user/authenticate" _ authenticate)
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (-> (routes app-routes)
      (wrap-authentication (token-handler env))
      (wrap-json-body {:keywords? true})
      (handler/site)
      (wrap-base-url)
      (wrap-trace :header :ui)
      (wrap-cors :access-control-allow-origin [#".*"]
                 :access-control-allow-methods [:get :put :post :delete :options])
      (wrap-json-response)))
