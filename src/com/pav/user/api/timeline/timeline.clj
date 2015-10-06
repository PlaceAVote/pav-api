(ns com.pav.user.api.timeline.timeline
  (:require [msgpack.core :as msg]
            [msgpack.clojure-extensions :refer :all]
            [taoensso.carmine :as car :refer (wcar)]
            [environ.core :refer [env]]
            [cheshire.core :as ch]))

(def server1-conn {:pool {} :spec {:uri (:redis-url env)}})

(defmacro wcar* [& body] `(car/wcar server1-conn ~@body))

(defrecord NewUserEvent [email first_name last_name topics])

(defn publish-newuser-evt [user]
  (let [new-user-evt (map->NewUserEvent {:email (:email user)
                                         :first_name (:first_name user)
                                         :last_name (:last_name user)
                                         :topics (:topics user)})]
    (wcar* (car/publish "NewUserEvt" (msg/pack new-user-evt)))))

(defn get-timeline [email]
  (mapv #(ch/parse-string % true) (wcar* (car/lrange (str email ":timeline") 0 -1))))
