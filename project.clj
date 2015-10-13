(defproject pav-user-api "0.1.0-SNAPSHOT"
  :description "User API for registering, authenticating and managing user profiles"
  :url ""
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [compojure "1.1.6"]
                 [hiccup "1.0.5"]
                 [ring-server "0.3.1"]
                 [http-kit "2.1.18"]
                 [ring/ring-json "0.3.1"]
                 [ring-cors "0.1.7"]
                 [prismatic/schema "0.4.3"]
                 [cheshire "5.5.0"]
                 [liberator "0.13"]
                 [environ "1.0.0"]
                 [buddy "0.6.1"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.clojure/java.jdbc "0.3.3"]
                 [org.flywaydb/flyway-core "3.0"]
                 [mysql/mysql-connector-java "5.1.6"]
                 [korma "0.4.2"]
                 [metosin/ring-swagger "0.21.0"]
                 [clojurewerkz/neocons "3.1.0-rc1"]
                 [com.taoensso/carmine "2.12.0"]
                 [com.taoensso/faraday "1.8.0"]
                 [clojure-msgpack "1.1.1"]]
  :plugins [[lein-ring "0.9.6"]
            [lein-environ "1.0.0"]
            [lein-beanstalk "0.2.7"]]
  :min-lein-version "2.0.0"
  :javac-options ["-target" "1.8" "-source" "1.8"]
  :main system
  :ring {:handler com.pav.user.api.handler/app
         :init com.pav.user.api.handler/init
         :destroy com.pav.user.api.handler/destroy
         :ssl? true
         :port 8080
         :ssl-port 8443
         :keystore "pavpkcs12.keystore"
         :key-password "password"}
  :aliases {"migrate" ["run" "-m" "pav-user-api.migrations.migrations/migrate"]
            "repair" ["run" "-m" "pav-user-api.migrations.migrations/repair"]}

  :profiles
  {
   :uberjar {:aot :all
             :env {:auth-pub-key "resources/pav_auth_pubkey.pem"}
             :uberjar-name "pav-user-api.jar"}
   :production
   {:ring
    {:open-browser? false, :stacktraces? false, :auto-reload? false}}
   :dev
   {:dependencies [[ring-mock "0.1.5"] [ring/ring-devel "1.3.1"]
                   [midje "1.7.0"]]
    :env {      :auth-priv-key "test-resources/pav_auth_privkey.pem"
                :auth-priv-key-pwd "password"
                :auth-pub-key "test-resources/pav_auth_pubkey.pem"
                :auth-pub-key-pwd "password"
                :mysql-database "pav_user"
                :mysql-port "3306"
                :mysql-host "localhost"
                :mysql-user "root"
                :mysql-password "root"
                :neo-url "http://localhost:7474/db/data/"
                :neo-username "neo4j"
                :neo-password "password"
                :redis-url "redis://127.0.0.1:6379"
                :access-key "Whatever"
                :secret-key "whatever"
                :dynamo-endpoint "http://localhost:8000"}
    :plugins [[lein-midje "3.1.3"]]}})
