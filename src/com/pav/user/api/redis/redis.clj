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

(defn create-user-profile [{:keys [user_id email facebook_id] :as user-profile}]
  (wcar redis-conn (do (car/hmset* (str "user:" user_id ":profile") (update-in user-profile [:created_at] bigint))
                       (car/set (str "email:" email ":id") user_id)))
	(when facebook_id
		(wcar redis-conn (car/set (str "facebookid:" facebook_id ":id") user_id))))

(defn delete-user-profile [{:keys [user_id email]}]
	(wcar redis-conn (do (car/del (str "user:" user_id ":profile"))
											 (car/del (str "email:" email ":id")))))

(defn get-user-profile [user_id]
  (let [profile (wcar redis-conn (car/parse-map (car/hgetall (str "user:" user_id ":profile")) :keywordize))]
    (if-not (empty? profile)
      (convert-to-correct-profile-type profile)
      nil)))

(defn get-user-profile-by-email [email]
  (let [user_id (wcar redis-conn (car/get (str "email:" email ":id")))]
    (get-user-profile user_id)))

(defn get-user-profile-by-facebook-id [facebook_id]
	(let [user_id (wcar redis-conn (car/get (str "facebookid:" facebook_id ":id")))]
		(get-user-profile user_id)))

(defn update-token [user_id new-token]
  (wcar redis-conn (car/hmset (str "user:" user_id ":profile") :token (:token new-token))))

(defn update-facebook-token [user_id new-facebook-token new-token]
  (wcar redis-conn (car/hmset (str "user:" user_id ":profile") :token (:token new-token) :facebook_token new-facebook-token)))

(defn create-password-reset-token [email reset-token]
	(wcar redis-conn
		(car/set (str "reset-token:" reset-token ":useremail") email)
		(car/set (str "useremail:" email ":reset-token") reset-token)))

(defn delete-password-reset-token [email reset-token]
	(wcar redis-conn
		(car/del (str "reset-token:" reset-token ":useremail") email)
		(car/del (str "useremail:" email ":reset-token") reset-token)))

(defn retrieve-password-reset-token-by-useremail [email]
	(wcar redis-conn (car/get (str "useremail:" email ":reset-token"))))

(defn retrieve-useremail-by-reset-token [token]
	(wcar redis-conn (car/get (str "reset-token:" token ":useremail"))))

(defn update-user-password [user_id password]
	(wcar redis-conn (car/hmset* (str "user:" user_id ":profile") {:password password})))

(defn update-account-settings [user_id param-map]
	(wcar redis-conn (car/hmset* (str "user:" user_id ":profile") param-map)))