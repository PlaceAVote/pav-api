(ns com.pav.user.api.handler
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
            [com.pav.user.api.resources.user :refer [list-users create create-facebook user authenticate user-timeline]]
            [com.pav.user.api.resources.docs :refer [swagger-docs]]
            [com.pav.user.api.authentication.authentication :refer [token-handler]]
            [com.pav.user.api.migrations.migrations :refer [migrate]]
            [liberator.dev :refer [wrap-trace]]
            [buddy.auth.middleware :refer [wrap-authentication]]
            [environ.core :refer [env]]
            [clojure.tools.logging :as log]))


(defn init []
  (log/info "API is starting")
  (log/info "Running database migration task")
  (migrate))

(defn destroy []
  (log/info "API is shutting down"))

(defroutes app-routes
  (GET "/docs" [] swagger-docs)
  (GET "/user/:email" [email] (user email))
  (GET "/user" [] list-users)
  (GET "/user/:email/timeline" [email] (user-timeline email))
  (PUT "/user" _ create)
  (PUT "/user/facebook" _ create-facebook)
  (POST "/user/authenticate" req (authenticate req :pav))
  (POST "/user/facebook/authenticate" req (authenticate req :facebook))
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
