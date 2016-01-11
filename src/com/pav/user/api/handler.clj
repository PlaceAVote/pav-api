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
            [com.pav.user.api.resources.user :refer [create create-facebook user authenticate
                                                     confirm-user notifications mark-notification timeline feed
                                                     follow following followers unfollow
                                                     user-profile validate-token reset-password confirm-password-reset
																										 user-settings]]
						[com.pav.user.api.notifications.ws-handler :refer [ws-notification-handler start-notification-listener]]
            [com.pav.user.api.resources.docs :refer [swagger-docs]]
            [com.pav.user.api.authentication.authentication :refer [token-handler]]
            [liberator.dev :refer [wrap-trace]]
            [buddy.auth.middleware :refer [wrap-authentication]]
            [environ.core :refer [env]]
            [clojure.tools.logging :as log]))


(defn init []
  (log/info "API is starting")
	(start-notification-listener))

(defn destroy []
  (log/info "API is shutting down"))

(defroutes app-routes
  (GET "/docs" [] swagger-docs)
	(POST "/user/me/settings" [] user-settings)
	(GET "/user/me/settings" [] user-settings)
  (GET "/user/me/profile" [] user-profile)
  (GET "/user/:user_id/profile" [user_id] user-profile)
  (GET "/user/feed" [] feed)
  (GET "/user/notifications" [] notifications)
	(GET "/user/notifications/ws" [token] ws-notification-handler)
  (POST "/user/notification/:notification_id/mark" [notification_id] (mark-notification notification_id))
  (GET "/user/me/timeline" [] timeline)
  (GET "/user/:user_id/timeline" [] timeline)
  (GET "/user/me/following" [] following)
  (GET "/user/:user_id/following" [user_id] following)
  (GET "/user/me/followers" [] followers)
  (GET "/user/:user_id/followers" [user_id] followers)
  (GET "/user/token/validate" [] validate-token)
  (PUT "/user" _ create)
	(DELETE "/user" [] user)
  (PUT "/user/follow" _ follow)
  (DELETE "/user/unfollow" _ unfollow)
  (PUT "/user/facebook" _ create-facebook)
  (POST "/user/authenticate" req (authenticate req :pav))
  (POST "/user/facebook/authenticate" req (authenticate req :facebook))
  (POST "/user/confirm/:confirm-token" [confirm-token] (confirm-user confirm-token))
  (POST "/password/reset" [email] (reset-password email))
	(POST "/password/reset/confirm" req confirm-password-reset)
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (-> (routes app-routes)
      (wrap-authentication (token-handler env))
      (wrap-json-body {:keywords? true})
      (handler/site)
      (wrap-cors :access-control-allow-origin [#".*"]
                 :access-control-allow-methods [:get :put :post :delete :options])
      (wrap-json-response)))

(defn start-server [options]
  (init)
  (run-server app options))
