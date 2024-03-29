(defproject pav-user-api "0.1.62-SNAPSHOT"
  :description "User API for registering, authenticating and managing user profiles"
  :url "https://github.com/PlaceAVote/pav-user-api"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [compojure "1.1.6"]
                 [hiccup "1.0.5"]
                 [ring-server "0.3.1"]
                 [http-kit "2.1.18"]
                 [ring/ring-json "0.3.1" :exclusions [ring/ring-core]]
                 [ring-cors "0.1.7" :exclusions [ring/ring-core]]
                 [prismatic/schema "0.4.3"]
                 [cheshire "5.5.0"]
                 [liberator "0.14.1"]
                 [environ "1.0.0"]
                 [buddy "0.6.1" :exclusions [clout
                                             clj-time
                                             slingshot
                                             org.clojure/tools.reader]]
                 [org.clojure/tools.logging "0.3.1"]
                 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail
                                                    javax.jms/jms
                                                    com.sun.jdmk/jmxtools
                                                    com.sun.jmx/jmxri]]
                 [com.taoensso/carmine "2.12.0" :exclusions [org.clojure/data.json
                                                             org.clojure/tools.reader]]
                 [clojure-msgpack "1.1.2"]
                 [clojurewerkz/elastisch "2.2.0-beta4"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [clj-http "2.0.0"]
                 [org.clojure/core.memoize "0.5.8"]
                 [clj-time "0.11.0"]
                 [amazonica "0.3.48" :exclustions [joda-time]]
                 [enlive "1.1.6"]
                 [com.taoensso/truss "1.0.0"]
                 [com.taoensso/faraday "1.9.0-beta1" :exclusions [joda-time
                                                                  com.amazonaws/aws-java-sdk-s3
                                                                  com.amazonaws/aws-java-sdk-kms
                                                                  com.amazonaws/aws-java-sdk-core]]
                 [com.taoensso/encore "2.33.0"]
                 [org.clojure/java.jdbc "0.6.1"]
                 [com.h2database/h2 "1.4.191"]
                 [mysql/mysql-connector-java "5.1.38"]
                 [org.flywaydb/flyway-core "4.0"]
                 [org.clojure/tools.cli "0.3.5"]]
  :plugins [[lein-environ "1.0.0"]
            [lein-release "1.0.5"]
            [lein-essthree "0.2.1" :exclusions [org.clojure/clojure]]]
  :essthree {:deploy {:type :library
                      :bucket "pav-maven-artifact-repository"
                      :snapshots     true
                      :sign-releases false
                      :checksum      :fail
                      :update        :always}
             :repository {:bucket "pav-maven-artifact-repository"}}
	:lein-release {:scm :git
                 :deploy-via :shell
                 :shell ["lein" "essthree"]
                 :build-uberjar true}
  :min-lein-version "2.0.0"
  :javac-options ["-target" "1.8" "-source" "1.8"]
  :main system
  :aliases {"migrate"       ["run" "-m" "com.pav.api.db.migrations/migrate!"]
            "repair"        ["run" "-m" "com.pav.api.db.migrations/repair!"]
            "info"          ["run" "-m" "com.pav.api.db.migrations/info"]
            "drop-tables"   ["run" "-m" "com.pav.api.db.db/drop-all-tables!"]
            "migrate-data"  ["run" "-m" "com.pav.api.dbwrapper.from-dynamodb/migrate-all-data"]}
  :profiles
  {
   :uberjar {:jvm-opts     ^:replace ["-Xms256m" "-Xmx512m" "-Xss512k" "-XX:MaxMetaspaceSize=150m"]
             :aot          [system com.pav.api.migrations.migrations]
             :env          {:auth-pub-key "resources/pav_auth_pubkey.pem"
                            :db-url       "h2:mem:pav"
                            :db-user      "pavuser"
                            :db-pwd       "pavpass"}
             :uberjar-name "pav-user-api.jar"}
   :production
            {:ring
             {:open-browser? false, :stacktraces? false, :auto-reload? false}}
   :dev
            {:dependencies [[ring-mock "0.1.5"] [ring/ring-devel "1.3.1" :exclusions [ring/ring-core]]
                            [midje "1.7.0" :exclusions [org.clojure/tools.macro]]
                            [org.clojure/test.check "0.9.0"]]
             :env          {:auth-priv-key                       "test-resources/pav_auth_privkey.pem"
                            :auth-priv-key-pwd                   "password"
                            :auth-pub-key                        "test-resources/pav_auth_pubkey.pem"
                            :auth-pub-key-pwd                    "password"
                            ;; db url should be in form: mysql://url/db or h2:file:///tmp/pav
                            ;:db-url                              "mysql://127.0.0.1:3306/pav"
                            ;:db-url                              "h2:file:///tmp/pav"
                            :db-url                              "h2:mem:pav"
                            :db-user                             "pavuser"
                            :db-pwd                              "pavpass"
                            :redis-url                           "redis://127.0.0.1:6379"
                            :access-key                          "WHATEVER"
                            :secret-key                          "WHATEVER"
                            :dynamo-endpoint                     "http://localhost:8000"
                            :dynamo-user-table-name              "users"
                            :dynamo-user-confirmation-table-name "user-confirmation-tokens"
                            :dynamo-notification-table-name      "notifications"
                            :dynamo-usertimeline-table-name      "usertimeline"
                            :dynamo-userfeed-table-name          "userfeed"
                            :dynamo-follower-table-name          "userfollowers"
                            :dynamo-following-table-name         "userfollowing"
                            :dynamo-bill-comments-table-name     "bill-comments"
                            :dynamo-comment-details-table-name   "comment-details"
                            :dynamo-comment-user-scoring-table   "user-comment-scoring-table"
                            :dynamo-user-votes-table             "user-votes"
                            :dynamo-vote-count-table             "vote-counts"
                            :dynamo-questions-table              "questions"
                            :dynamo-user-question-answers-table	 "user-question-answers"
                            :dynamo-user-issues-table            "user-issues"
                            :dynamo-user-issue-responses-table   "user-issues-responses"
                            :dynamo-user-issue-comments-table-name "user-issue-comments"
                            :dynamo-user-issue-comments-scoring-table "user-issues-scores"
                            :redis-notification-pubsub           "redis::notifications::pubsub"
                            :es-url                              "http://localhost:9200"
                            :email-mode                          "test"
                            :s3-upload-allowed                   "false"
                            :mandril-api-key                     "key"
                            :mandril-pwd-reset-template          "password-reset-template-dev"
                            :mandril-welcome-email-template      "welcome-email-dev"
                            :mandril-comment-reply-template      "comment-reply-template-dev"
                            :mandril-email-confirmation-template "confirm-email-dev"
                            :mandril-invite-user-template        "invite-user-dev"
                            :cdn-bucket-name                     "placeavote-cdn"
                            :cdn-url                             "https://cdn.placeavote.com"
                            :s3-region                           "us-west-1"
                            :default-followers                   "wam@stuff.com,wam2@pl.com"
                            :sunlight-congress-apikey            "4a8d55c9cf12410e95ad2c09615a46a4"
                            :google-geolocation-apikey           "AIzaSyB2taZNvNDGG0Fvyur-o3Xf0g6vd8sYuUM"
                            :facebook-mode                       "test"
                            :facebook-app-id                     "TEST"
                            :facebook-client-app-secret          "TEST"
                            :sql-backend-enabled                 "false"}
             :plugins      [[lein-midje "3.1.3"]]}})
