(defproject pav-user-api "0.1.13-SNAPSHOT"
  :description "User API for registering, authenticating and managing user profiles"
  :url "https://github.com/PlaceAVote/pav-user-api"
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
                 [com.taoensso/faraday "1.8.0" :exclusions [org.clojure/tools.reader]]
                 [clojure-msgpack "1.1.2"]
                 [clojurewerkz/elastisch "2.2.0-beta4"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [clojurewerkz/mailer "1.2.0"]
                 [clj-http "2.0.0"]
                 [org.clojure/core.memoize "0.5.8"]
								 [amazonica "0.3.48"]
                 [enlive "1.1.6"]
                 [com.taoensso/truss "1.0.0"]]
  :plugins [[lein-environ "1.0.0"]
						[lein-release "1.0.5"]
            [lein-essthree "0.2.1"]]
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
  :profiles
  {
   :uberjar {:jvm-opts     ^:replace ["-Xms256m" "-Xmx512m" "-Xss512k" "-XX:MaxMetaspaceSize=150m"]
             :aot          [system]
             :env          {:auth-pub-key "resources/pav_auth_pubkey.pem"}
             :uberjar-name "pav-user-api.jar"}
   :production
            {:ring
             {:open-browser? false, :stacktraces? false, :auto-reload? false}}
   :dev
            {:dependencies [[ring-mock "0.1.5"] [ring/ring-devel "1.3.1" :exclusions [ring/ring-core]]
                            [midje "1.7.0" :exclusions [org.clojure/tools.macro]]]
             :env          {:auth-priv-key                       "test-resources/pav_auth_privkey.pem"
                            :auth-priv-key-pwd                   "password"
                            :auth-pub-key                        "test-resources/pav_auth_pubkey.pem"
                            :auth-pub-key-pwd                    "password"
                            :redis-url                           "redis://127.0.0.1:6379"
                            :access-key                          "Whatever"
                            :secret-key                          "whatever"
                            :dynamo-endpoint                     "http://localhost:8000"
                            :dynamo-user-table-name              "users"
                            :dynamo-user-confirmation-table-name "user-confirmation-tokens"
                            :dynamo-notification-table-name      "notifications"
                            :dynamo-usertimeline-table-name      "usertimeline"
                            :dynamo-userfeed-table-name          "userfeed"
                            :dynamo-follower-table-name          "userfollowers"
                            :dynamo-following-table-name         "userfollowing"
                            :dynamo-comment-details-table-name	 "comment-details"
                            :dynamo-vote-count-table             "vote-counts"
                            :dynamo-questions-table              "questions"
                            :dynamo-user-question-answers-table	 "user-question-answers"
                            :dynamo-user-issues-table            "user-issues"
                            :dynamo-user-issue-responses-table   "user-issue-responses"
                            :redis-notification-pubsub           "redis::notifications::pubsub"
                            :es-url                              "http://localhost:9200"
                            :timeline-queue                      "redismq::queue_name::user-timelineevent-queue"
                            :email-host                          "smtp.mandrillapp.com"
                            :email-user                          "team@placeavote.com"
                            :email-pass                          "password"
                            :email-mode                          "test"
                            :email-port                          "587"
                            :mandril-api-key                     "key"
                            :mandril-pwd-reset-template          "password-reset-template-dev"
                            :cdn-bucket-name                     "placeavote-cdn"
                            :cdn-url                             "https://cdn.placeavote.com"
                            :s3-region                           "us-west-1"
                            :default-followers                   "wam@stuff.com,wam2@pl.com"}
             :plugins      [[lein-midje "3.1.3"]]}})
