(ns com.pav.api.test.db.db-test
  "Testing code for lowlevel database functions."
  (:use midje.sweet
        clojure.test)
  (:require [com.pav.api.db.db :as d]
            [environ.core :refer [env]]))

(deftest db-functions
  (fact "Test parsing database url for different databases."
    (d/parse-url "mysql://127.0.0.1:3306/pav") => {:subprotocol "mysql" :subname "//127.0.0.1:3306/pav"}
    (d/parse-url "mysql://127.0.0.1:3306/pav?var1=val&var2=val") =>
               {:subprotocol "mysql" :subname "//127.0.0.1:3306/pav?var1=val&var2=val"}
    (d/parse-url "h2:file:///tmp/pav") => {:subprotocol "h2" :subname "file:///tmp/pav;mode=mysql;database_to_upper=false"}
    (d/parse-url "h2:mem:pav") => {:subprotocol "h2" :subname "mem:pav;mode=mysql;database_to_upper=false;db_close_delay=-1"})

  (fact "Test setting and resetting database object in runtime."
    (let [old (:db-url env)]
      (d/set-db-url! "h2:file:///some-magic-file")
      d/db => (contains {:subprotocol "h2" :subname "file:///some-magic-file;mode=mysql;database_to_upper=false"})
      (d/set-db-url! "mysql://127.0.0.1:3306/mydb")
      d/db => (contains {:subprotocol "mysql" :subname "//127.0.0.1:3306/mydb"})
      (d/set-db-url! old))))
 
