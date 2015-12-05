(ns com.pav.user.api.redis.redis
  (:require [environ.core :refer [env]]
            [taoensso.carmine :as car :refer (wcar)]
            [taoensso.carmine.message-queue :as car-mq]
            [msgpack.core :as msg]
            [msgpack.clojure-extensions]
            [cheshire.core :as ch]
            [com.pav.user.api.domain.user :refer [convert-to-correct-profile-type]]))

(def redis-conn {:spec {:uri (:redis-url env)}})
(def timeline-queue (:timeline-queue env))

(defn publish-to-timeline [event]
  (wcar redis-conn (car-mq/enqueue timeline-queue (-> (ch/generate-string event) msg/pack))))

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

(defn update-token [user_id new-token]
  (wcar redis-conn (car/hmset (str "user:" user_id ":profile") :token (:token new-token))))

(defn update-facebook-token [user_id new-facebook-token new-token]
  (wcar redis-conn (car/hmset (str "user:" user_id ":profile") :token (:token new-token) :facebook_token new-facebook-token)))
