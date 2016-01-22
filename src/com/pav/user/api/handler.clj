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
						[liberator.dev :refer [wrap-trace]]
						[buddy.auth.middleware :refer [wrap-authentication]]
						[environ.core :refer [env]]
						[clojure.tools.logging :as log]
						[clojure.edn :as edn]
            [com.pav.user.api.resources.user :refer [create create-facebook user authenticate
                                                     confirm-user notifications mark-notification timeline feed
                                                     follow following followers unfollow
                                                     user-profile validate-token reset-password confirm-password-reset
																										 user-settings change-password questions upload-profile-image]]
						[com.pav.user.api.notifications.ws-handler :refer [ws-notification-handler start-notification-listener]]
            [com.pav.user.api.resources.docs :refer [swagger-docs]]
            [com.pav.user.api.authentication.authentication :refer [token-handler]]
						[com.pav.user.api.services.questions :refer [bootstrap-wizard-questions]]))


(defn init []
  (log/info "API is starting")
	(start-notification-listener)
	(bootstrap-wizard-questions (edn/read-string (slurp "resources/questions.edn"))))

(defn destroy []
  (log/info "API is shutting down"))

(defroutes app-routes
  (GET "/docs" [] swagger-docs)
  (POST "/user/me/settings" [] user-settings)
  (GET "/user/me/settings" [] user-settings)
  (GET "/user/me/profile" [] user-profile)
  (POST "/user/me/profile/image" [file] (upload-profile-image file))
  (GET "/user/:user_id/profile" [_] user-profile)
  (GET "/user/feed" [] feed)
  (GET "/user/questions" [] questions)
  (POST "/user/questions" [] questions)
  (GET "/user/notifications" [] notifications)
  (GET "/user/notifications/ws" [_] ws-notification-handler)
  (POST "/user/notification/:notification_id/mark" [notification_id] (mark-notification notification_id))
  (GET "/user/me/timeline" [] timeline)
  (GET "/user/:user_id/timeline" [] timeline)
  (GET "/user/me/following" [] following)
  (GET "/user/:user_id/following" [_] following)
  (GET "/user/me/followers" [] followers)
  (GET "/user/:user_id/followers" [_] followers)
  (GET "/user/token/validate" [] validate-token)
  (PUT "/user" _ create)
  (DELETE "/user" [] user)
  (PUT "/user/follow" _ follow)
  (PUT "/user/facebook" _ create-facebook)
  (DELETE "/user/unfollow" _ unfollow)
  (POST "/user/authenticate" _ (authenticate :pav))
  (POST "/user/facebook/authenticate" _ (authenticate :facebook))
  (POST "/user/confirm/:confirm-token" [confirm-token] (confirm-user confirm-token))
  (POST "/password/reset" [email] (reset-password email))
  (POST "/password/reset/confirm" _ confirm-password-reset)
  (POST "/password/change" _ change-password)
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (-> (routes app-routes)
      (wrap-authentication (token-handler env))
      (wrap-json-body {:keywords? true})
      (wrap-cors :access-control-allow-origin [#".*"]
                 :access-control-allow-methods [:get :put :post :delete :options])
			(handler/site)
      (wrap-json-response)))

(defn start-server [options]
  (init)
  (run-server app options))
