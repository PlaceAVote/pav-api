(ns com.pav.user.api.redis.redis
  (:require [environ.core :refer [env]]
            [taoensso.carmine :as car :refer (wcar)]
            [taoensso.carmine.message-queue :as car-mq]
            [msgpack.core :as msg]
            [msgpack.clojure-extensions]
            [cheshire.core :as ch]))

(def redis-conn {:spec {:uri (:redis-url env)}})
(def user-event-queue (:user-event-queue env))

(defn get-user-timeline [user]
  (->> (wcar redis-conn (car/zrevrange (str "timeline:" user) 0 -1))
       (mapv msg/unpack)
       (mapv #(ch/parse-string % true))))

(defn publish-to-timeline [event]
  (wcar redis-conn (car-mq/enqueue user-event-queue (-> (ch/generate-string event)
                                                   msg/pack))))
