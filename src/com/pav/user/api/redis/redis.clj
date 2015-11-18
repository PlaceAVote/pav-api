(ns com.pav.user.api.redis.redis
  (:require [environ.core :refer [env]]
            [taoensso.carmine :as car :refer (wcar)]
            [msgpack.core :as msg]
            [msgpack.clojure-extensions]
            [cheshire.core :as ch]))

(def redis-conn {:spec {:uri (:redis-url env)}})

(defn get-user-timeline [user]
  (->> (wcar redis-conn (car/zrevrange (str "timeline:" user) 0 -1))
       (mapv msg/unpack)
       (mapv #(ch/parse-string % true))))

(defn publish-to-timeline [event]
  (let [timeline-key (str "timeline:" (:user_id event))]
    (wcar redis-conn (car/zadd timeline-key (:timestamp event) (msg/pack event)))))
