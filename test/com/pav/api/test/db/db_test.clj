(ns com.pav.api.test.db.db-test
  "Testing code for lowlevel database functions."
  (:use clojure.test)
  (:require [com.pav.api.db.db :as d]
            [com.pav.api.test.utils.utils :refer [contains-map?]]
            [environ.core :refer [env]]))

(deftest db-functions
  (testing "Test parsing database url for different databases."
    (is (= (d/parse-url "mysql://127.0.0.1:3306/pav") {:subprotocol "mysql" :subname "//127.0.0.1:3306/pav"}))
    (is (= (d/parse-url "mysql://127.0.0.1:3306/pav?var1=val&var2=val")
           {:subprotocol "mysql" :subname "//127.0.0.1:3306/pav?var1=val&var2=val"}))
    (is (= (d/parse-url "h2:file:///tmp/pav")
           {:subprotocol "h2" :subname "file:///tmp/pav;mode=mysql;database_to_upper=false"}))
    (is (= (d/parse-url "h2:mem:pav")
           {:subprotocol "h2" :subname "mem:pav;mode=mysql;database_to_upper=false;db_close_delay=-1"})))

  (testing "Test setting and resetting database object in runtime."
    (let [old (:db-url env)]
      (d/set-db-url! "h2:file:///some-magic-file")
      (is (contains-map? d/db {:subprotocol "h2" :subname "file:///some-magic-file;mode=mysql;database_to_upper=false"}))
      (d/set-db-url! "mysql://127.0.0.1:3306/mydb")
      (is (contains-map? d/db {:subprotocol "mysql" :subname "//127.0.0.1:3306/mydb"}))
      (d/set-db-url! old))))
 
