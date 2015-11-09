(defproject pav-user-api "0.1.0-SNAPSHOT"
  :description "User API for registering, authenticating and managing user profiles"
  :url ""
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [compojure "1.1.6"]
                 [hiccup "1.0.5"]
                 [ring-server "0.3.1"]
                 [http-kit "2.1.18"]
                 [ring/ring-json "0.3.1" :exclusions [ring/ring-core]]
                 [ring-cors "0.1.7" :exclusions [ring/ring-core]]
                 [prismatic/schema "0.4.3"]
                 [cheshire "5.5.0"]
                 [liberator "0.13"]
                 [environ "1.0.0"]
                 [buddy "0.6.1"]
                 [org.clojure/tools.logging "0.3.1"]
                 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail
                                                    javax.jms/jms
                                                    com.sun.jdmk/jmxtools
                                                    com.sun.jmx/jmxri]]
                 [com.taoensso/carmine "2.12.0" :exclusions [org.clojure/data.json
                                                             org.clojure/tools.reader]]
                 [com.taoensso/faraday "1.8.0" :exclusions [org.clojure/tools.reader]]
                 [clojure-msgpack "1.1.2"]]
  :plugins [[lein-environ "1.0.0"]]
  :min-lein-version "2.0.0"
  :javac-options ["-target" "1.8" "-source" "1.8"]
  :main system
  :profiles
  {
   :uberjar {:aot :all
             :env {:auth-pub-key "resources/pav_auth_pubkey.pem"}
             :uberjar-name "pav-user-api.jar"}
   :production
   {:ring
    {:open-browser? false, :stacktraces? false, :auto-reload? false}}
   :dev
   {:dependencies [[ring-mock "0.1.5"] [ring/ring-devel "1.3.1" :exclusions [ring/ring-core]]
                   [midje "1.7.0" :exclusions [org.clojure/tools.macro]]]
    :env {      :auth-priv-key "test-resources/pav_auth_privkey.pem"
                :auth-priv-key-pwd "password"
                :auth-pub-key "test-resources/pav_auth_pubkey.pem"
                :auth-pub-key-pwd "password"
                :redis-url "redis://127.0.0.1:6379"
                :access-key "Whatever"
                :secret-key "whatever"
                :dynamo-endpoint "http://localhost:8000"
                :dynamo-user-table-name "users"
                :dynamo-user-confirmation-table-name "user-confirmation-tokens"
                :dynamo-notification-table-name "notifications"}
    :plugins [[lein-midje "3.1.3"]]}})
