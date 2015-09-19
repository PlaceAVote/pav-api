(ns pav-user-api.handler
  (:require [compojure.core :refer [defroutes routes]]
            [org.httpkit.server :refer [run-server]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.json :refer [wrap-json-body wrap-json-params wrap-json-response]]
            [ring.middleware.file-info :refer [wrap-file-info]]
            [ring.middleware.cors :refer [wrap-cors]]
            [hiccup.middleware :refer [wrap-base-url]]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer :all]
            [pav-user-api.resources.user :refer [list-users create create-facebook user authenticate]]
            [pav-user-api.resources.docs :refer [swagger-docs]]
            [pav-user-api.authentication.authentication :refer [token-handler]]
            [pav-user-api.migrations.migrations :refer [migrate]]
            [liberator.dev :refer [wrap-trace]]
            [buddy.auth.middleware :refer [wrap-authentication]]
            [environ.core :refer [env]]
            [clojure.tools.logging :as log]))


(defn init []
  (log/info (str "Environment Variables " env))
  (log/info "pav-user-api is starting")
  (log/info "Running database migration task")
  (migrate))

(defn destroy []
  (log/info "pav-user-api is shutting down"))

(defroutes app-routes
  (GET "/docs" [] swagger-docs)
  (GET "/user/:email" [email] (user email))
  (GET "/user" [] list-users)
  (PUT "/user" _ create)
  (PUT "/user/facebook" _ create-facebook)
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

(defn start-server [options]
  (init)
  (run-server app options))
