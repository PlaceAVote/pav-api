(ns com.pav.user.api.redis.redis
  (:require [environ.core :refer [env]]
            [taoensso.carmine :as car :refer (wcar)]
            [taoensso.carmine.message-queue :as car-mq]
            [msgpack.core :as msg]
            [msgpack.clojure-extensions]
            [cheshire.core :as ch]))

(def redis-conn {:spec {:uri (:redis-url env)}})
(def user-event-queue (:user-event-queue env))

(defn get-user-timeline
  ([user & [from]]
   (let [offset (or from 0)
         to (+ offset 9)
         results (->> (wcar redis-conn (car/zrevrange (str "timeline:" user) offset to))
                      (mapv msg/unpack)
                      (mapv #(ch/parse-string % true)))
         next-page (or (and (< (count results) 10) 0)
                       (inc to))]
     {:next-page next-page
      :results results})))

(defn publish-to-timeline [event]
  (wcar redis-conn (car-mq/enqueue user-event-queue (-> (ch/generate-string event)
                                                   msg/pack))))
