(ns com.pav.api.facebook.facebook-service
  (:require [environ.core :refer [env]]
            [clj-http.client :as http]
            [clojure.tools.logging :as log]))

(def  ^{:doc "Registered Facebook APP ID"}
  facebook-app-id (:facebook-app-id env))

(def  ^{:doc "Facebook App secret"}
  facebook-secret (:facebook-client-app-secret env))

(def ^{:doc "Facebook Graph API Endpoint"}
  graph-api-endpoint "https://graph.facebook.com/oauth")

(def email-mode (:email-mode env))

(defn generate-long-lived-token
  "Exchanges short lived token for a long lived token"
  [short-lived-token]
  (if (= email-mode "live")
    (let [response (-> (http/get (str graph-api-endpoint "/access_token")
                         {:accept :json
                          :as     :clojure
                          :query-params {:grant_type    "fb_exchange_token" :client_id facebook-app-id
                                         :client_secret facebook-secret :fb_exchange_token short-lived-token}})
                     :body)]
      (log/info "Facebook Token response " response)
      (or (:access_token response) short-lived-token))
    short-lived-token))

