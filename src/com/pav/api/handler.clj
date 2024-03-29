(ns com.pav.api.handler
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
            [clojure.string :refer [lower-case]]
            [com.pav.api.resources.legislator :refer [get-legislator]]
            [com.pav.api.resources.reports :refer [activity-report]]
            [com.pav.api.resources.bill :refer [get-bill get-trending-bills create-comment update-comment
                                                get-comments delete-comment
                                                top-comments create-comment-reply like-comment dislike-comment]]
            [com.pav.api.resources.search :refer [search-term search-with-tag]]
            [com.pav.api.resources.vote :refer [cast-vote get-vote-count get-vote-records]]
            [com.pav.api.resources.user :refer [create create-facebook user authenticate
                                                confirm-user notifications mark-notification timeline feed
                                                follow following followers unfollow
                                                user-profile validate-token reset-password confirm-password-reset
                                                user-settings change-password questions upload-profile-image
                                                create-user-issue create-user-issue-comment delete-user-issue
                                                get-user-issue user-issue-emotional-response user-issue-comments
                                                update-user-issue feed contact-form validate-user
                                                update-user-issue-comment delete-user-issue-comment
                                                like-user-issue-comment dislike-user-issue-comment scrape-link
                                                invite-users mark-all-notifications]]
            [com.pav.api.notifications.ws-handler :refer [ws-notification-handler start-notification-listener]]
            [com.pav.api.resources.docs :refer [swagger-docs]]
            [com.pav.api.authentication.authentication :refer [token-handler]]
            [com.pav.api.services.questions :refer [bootstrap-wizard-questions]]
            [com.pav.api.dbwrapper.helpers :as db]))


(defn init []
  (log/info "API is starting")
  (start-notification-listener)
  (-> "resources/questions.edn"
      slurp
      ;; FIXME: use EDN reader to capture possible edn format issues
      edn/read-string
      bootstrap-wizard-questions))

(defn destroy []
  (log/info "API is shutting down"))

(defroutes app-routes
  (GET "/docs" [] swagger-docs)
  (POST "/user/invite" [] invite-users)
  (POST "/user/me/settings" [] user-settings)
  (GET "/user/me/settings" [] user-settings)
  (GET "/user/me/profile" [] user-profile)
  (POST "/user/me/profile/image" [] upload-profile-image)
  (GET "/user/:user_id/profile" [_] user-profile)
  (GET "/user/feed" [from] (feed from))
  (GET "/user/questions" [] questions)
  (POST "/user/questions" [] questions)
  (GET "/user/notifications" [from] (notifications from))
  (GET "/user/notifications/ws" [_] ws-notification-handler)
  (POST "/user/notification/:notification_id/mark" [notification_id] (mark-notification notification_id))
  (POST "/user/notification/markall" [] mark-all-notifications)
  (GET "/user/me/timeline" [from] (timeline from))
  (GET "/user/:user_id/timeline" [from] (timeline from))
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
  (POST "/password/reset" [email] (reset-password (lower-case email)))
  (POST "/password/reset/confirm" _ confirm-password-reset)
  (POST "/password/change" _ change-password)
  (PUT "/user/issue" [] create-user-issue)
  (PUT "/user/issue/comment" [] create-user-issue-comment)
  (POST "/user/issue/:issue_id" [issue_id] (update-user-issue issue_id))
  (DELETE "/user/issue/:issue_id" [issue_id] (delete-user-issue issue_id))
  (GET "/user/issue/:issue_id" [issue_id] (get-user-issue issue_id))
  (POST "/user/issue/:issue_id/response" [issue_id] (user-issue-emotional-response issue_id))
  (GET "/user/issue/:issue_id/response" [issue_id] (user-issue-emotional-response issue_id))
  (DELETE "/user/issue/:issue_id/response" [issue_id] (user-issue-emotional-response issue_id))
  (GET "/user/issue/:issue_id/comments" [issue_id] (user-issue-comments issue_id))
  (POST "/user/issue/comments/:comment_id" [comment_id] (update-user-issue-comment comment_id))
  (DELETE "/user/issue/comments/:comment_id" [comment_id] (delete-user-issue-comment comment_id))
  (POST "/user/issue/comments/:comment_id/like" [comment_id] (like-user-issue-comment comment_id))
  (DELETE "/user/issue/comments/:comment_id/like" [comment_id] (like-user-issue-comment comment_id))
  (POST "/user/issue/comments/:comment_id/dislike" [comment_id] (dislike-user-issue-comment comment_id))
  (DELETE "/user/issue/comments/:comment_id/dislike" [comment_id] (dislike-user-issue-comment comment_id))
  (POST "/user/contact" _ contact-form)
  (GET "/search/bills" [tag] (search-with-tag tag))
  (GET "/search" [term] (search-term term))
  (PUT "/vote" _ cast-vote)
  (GET "/vote/count" [bill-id] (get-vote-count bill-id))
  (GET "/vote/bill/:bill-id" [bill-id] (get-vote-records bill-id))
  (GET "/legislators/:thomas" [thomas] (get-legislator thomas))
  (GET "/bills/trending" [] get-trending-bills)
  (GET "/bills/:bill_id" [bill_id] (get-bill bill_id))
  (GET "/bills/:bill_id/comments" [bill_id] (get-comments bill_id))
  (GET "/bills/:bill_id/topcomments" [bill_id] (top-comments bill_id))
  (PUT "/bills/comments" _ create-comment)
  (PUT "/comments/:comment_id/reply" [comment_id] (create-comment-reply comment_id))
  (POST "/comments/:comment_id/like" [comment_id] (like-comment comment_id))
  (POST "/comments/:comment_id" [comment_id] (update-comment comment_id))
  (DELETE "/comments/:comment_id" [comment_id] (delete-comment comment_id))
  (DELETE "/comments/:comment_id/like" [comment_id] (like-comment comment_id))
  (POST "/comments/:comment_id/dislike" [comment_id] (dislike-comment comment_id))
  (DELETE "/comments/:comment_id/dislike" [comment_id] (dislike-comment comment_id))
  (POST "/user/validate" [] validate-user)
  (GET "/reports/activity" [weeks] (activity-report weeks))
  (GET "/opengraph/scrape" [link] (scrape-link link))
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (-> app-routes
      routes
      (wrap-authentication (token-handler env))
      (wrap-json-body {:keywords? true})
      (wrap-cors :access-control-allow-origin [#".*"]
                 :access-control-allow-methods [:get :put :post :delete :options])
      handler/site
      wrap-json-response))

(defn start-server [options]
  (init)
  (run-server #'app options))
