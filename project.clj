(defproject pav-user-api "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [compojure "1.1.6"]
                 [metosin/compojure-api "0.23.0"]
                 [hiccup "1.0.5"]
                 [ring-server "0.3.1"]
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
                 [korma "0.4.2"]]
  :plugins [[lein-ring "0.8.12"] [lein-environ "1.0.0"]]
  :ring {:handler pav-user-api.handler/app
         :init pav-user-api.handler/init
         :destroy pav-user-api.handler/destroy
         :ssl? true
         :port 8080
         :ssl-port 8443
         :keystore "pavpkcs12.keystore"
         :key-password "password"}
  :aliases {"migrate" ["run" "-m" "pav-user-api.migrations.migrations/migrate"]
            "repair" ["run" "-m" "pav-user-api.migrations.migrations/repair"]}
  :profiles
  {:uberjar {:aot :all}
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
                :mysql-password "root"}
    :plugins [[lein-midje "3.1.3"]]}})
