(defproject pav-user-api "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [compojure "1.1.6"]
                 [hiccup "1.0.5"]
                 [ring-server "0.3.1"]
                 [ring/ring-json "0.3.1"]
                 [prismatic/schema "0.4.3"]
                 [clojurewerkz/neocons "3.1.0-beta3"]
                 [cheshire "5.5.0"]
                 [liberator "0.13"]
                 [environ "1.0.0"]
                 [buddy "0.6.0"]
                 [com.taoensso/carmine "2.11.1"]]
  :plugins [[lein-ring "0.8.12"] [lein-environ "1.0.0"]]
  :ring {:handler pav-user-api.handler/app
         :init pav-user-api.handler/init
         :destroy pav-user-api.handler/destroy}
  :profiles
  {:uberjar {:aot :all}
   :production
   {:ring
    {:open-browser? false, :stacktraces? false, :auto-reload? false}}
   :dev
   {:dependencies [[ring-mock "0.1.5"] [ring/ring-devel "1.3.1"]
                   [midje "1.7.0"]]
    :env {:neo-url "http:localhost:7474/db/data"
          :neo-username "neo4j"
          :neo-password "password"
          :redis-url "http://localhost:6379"
          :auth-priv-key "test-resources/pav_auth_privkey.pem"
          :auth-priv-key-pwd "password"}}
   :plugins [[lein-midje "3.1.3"]]})
