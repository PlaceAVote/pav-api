(ns com.pav.api.test.db.generators
  "Helpers for easier data generation."
  (:require [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]))

(def domain 
  "Default set of domains for randomization."
  (gen/elements ["gmail.com"
                 "yahoo.com"
                 "hotmail.com"
                 "placeavote.com"
                 "mymail.net"]))

(def email-name
  "email name generator, which makes sure user name has more than on character."
  ;(gen/such-that #(> (count %) 3) gen/string-ascii)
  (gen/fmap #(apply str %)
            (gen/vector gen/char-ascii 3 200)))

(def email-gen
  "email generator, combining email-name and domain."
  (gen/fmap (fn [[name domain-name]]
              (str name "@" domain-name))
            (gen/tuple (gen/not-empty email-name) domain)))

(def gen-uuid-str
  "UUID generator, that produces UUID as string. gen/uuid produces UUID object."
  (gen/fmap str gen/uuid))

(def creds-gen
  "Credentials generator with randomization option between making password or
facebook tokens."
  (gen/one-of [(gen/hash-map :password (gen/not-empty gen/string-alphanumeric))
               (gen/hash-map :facebook_id gen-uuid-str
                             :facebook_token gen-uuid-str)]))

(def zipcode-gen
  "Zipcode generator. Limited to produce string with 5 random characters."
  (gen/fmap #(apply str %) 
            (gen/vector gen/char-ascii 5)))

(def lat-long-gen
  "Generator for latitude/longtititude values"
  (gen/double* {:infinite? false :NaN? false :max 99.999 :min -99.999}))

(def user-gen
  "User map generator."
  (gen/hash-map :email email-gen
                :first_name gen/string-alphanumeric
                :last_name  gen/string-alphanumeric
                :img_url    gen/string-alphanumeric
                :gender     (gen/elements ["male" "female" "they"])
                :dob        gen/nat
                :address    gen/string-ascii
                :zipcode    zipcode-gen
                :district   gen/nat
                :state      gen/string-alphanumeric
                :confirmation-token gen-uuid-str
                :latitude   lat-long-gen
                :longtitude lat-long-gen
                :public_profile gen/boolean
                :old_user_id gen-uuid-str
                :created_at gen/nat
                :updated_at gen/nat))
