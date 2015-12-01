(ns com.pav.user.api.redis.redis
  (:require [environ.core :refer [env]]
            [taoensso.carmine :as car :refer (wcar)]
            [taoensso.carmine.message-queue :as car-mq]
            [msgpack.core :as msg]
            [msgpack.clojure-extensions]
            [cheshire.core :as ch]
            [com.pav.user.api.domain.user :refer [convert-to-correct-profile-type]]))

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

(defn create-user-profile [{:keys [user_id email] :as user-profile}]
  (wcar redis-conn (do (car/hmset* (str "user:" user_id ":profile") (update-in user-profile [:created_at] bigint))
                       (car/set (str "email:" email ":id") user_id))))

(defn get-user-profile [user_id]
  (let [profile (wcar redis-conn (car/parse-map (car/hgetall (str "user:" user_id ":profile")) :keywordize))]
    (if-not (empty? profile)
      (convert-to-correct-profile-type profile)
      nil)))

(defn get-user-profile-by-email [email]
  (let [user_id (wcar redis-conn (car/get (str "email:" email ":id")))]
    (get-user-profile user_id)))

(defn retrieve-redis-notification [notification_id]
  (wcar redis-conn (car/parse-map (car/hgetall notification_id) :keywordize)))

(defn retrieve-redis-notifications [user_id]
  (->> (wcar redis-conn (car/zrevrange (str "user:" user_id ":notifications") 0 20))
       (mapv retrieve-redis-notification)))

(defn update-token [user_id new-token]
  (wcar redis-conn (car/hmset (str "user:" user_id ":profile") :token (:token new-token))))

(defn update-facebook-token [user_id new-facebook-token new-token]
  (wcar redis-conn (car/hmset (str "user:" user_id ":profile") :token (:token new-token) :facebook_token new-facebook-token)))
