(ns com.pav.api.redis.redis
  (:require [environ.core :refer [env]]
            [taoensso.carmine :as car :refer (wcar)]
            [taoensso.carmine.message-queue :as car-mq]
            [msgpack.core :as msg]
            [msgpack.clojure-extensions]
            [cheshire.core :as ch]
            [clj-time.core :as t]
            [com.pav.api.domain.user :refer [convert-to-correct-profile-type]]))

(def redis-conn {:spec {:uri (:redis-url env)}})
(def timeline-queue (:timeline-queue env))
(def notification-queue (:notification-queue env))
(def email-notification-queue (:email-notification-queue env))

(defn queue-event [queue event]
  (wcar redis-conn (car-mq/enqueue queue (-> event ch/generate-string msg/pack))))

(defn publish-bill-comment [comment]
  (queue-event timeline-queue (assoc comment :type "comment")))

(defn publish-bill-comment-reply [comment]
  (queue-event notification-queue (assoc comment :type "commentreply")))

(defn publish-bill-comment-email-reply [comment]
  (queue-event email-notification-queue (assoc comment :type "commentreply")))

(defn publish-scoring-comment-evt [comment user_id operation]
  (case operation
    :like (queue-event timeline-queue (assoc comment :type "likecomment" :user_id user_id))
    :dislike (queue-event timeline-queue (assoc comment :type "dislikecomment" :user_id user_id))))

(defn upk [user_id]
	"Create user profile key"
	(str "user:" user_id ":profile"))

(defn uek [email]
	"Create reverse lookup key using the users email"
	(str "email:" email ":id"))

(defn ufbidk [facebook_id]
	"Create reverse lookup key using a users facebook ID"
	(str "facebookid:" facebook_id ":id"))

(defn reset-email-key [reset-token]
	"Create an email lookup key given a users reset token."
	(str "reset-token:" reset-token ":useremail"))

(defn useremail-reset-key [email]
	"Create a reset token lookup key given a users email"
	(str "useremail:" email ":reset-token"))

(defn create-user-profile [{:keys [user_id email facebook_id] :as user-profile}]
  (wcar redis-conn (do (car/hmset* (upk user_id) (update-in user-profile [:created_at] bigint))
                       (car/set (uek email) user_id)))
	(when facebook_id
		(wcar redis-conn (car/set (ufbidk facebook_id) user_id))))

(defn delete-user-profile [{:keys [user_id email]}]
	(wcar redis-conn (do (car/del (upk user_id))
											 (car/del (uek email)))))

(defn get-user-profile [user_id]
  (let [profile (wcar redis-conn (car/parse-map (car/hgetall (upk user_id)) :keywordize))]
    (if (seq profile)
      (convert-to-correct-profile-type profile)
      nil)))

(defn get-user-profile-by-email [email]
  (let [user_id (wcar redis-conn (car/get (uek email)))]
    (get-user-profile user_id)))

(defn get-user-profile-by-facebook-id [facebook_id]
	(let [user_id (wcar redis-conn (car/get (ufbidk facebook_id)))]
		(get-user-profile user_id)))

(defn update-token [user_id new-token]
  (wcar redis-conn (car/hmset (upk user_id) :token (:token new-token))))

(defn update-facebook-token [user_id new-facebook-token new-token]
  (wcar redis-conn (car/hmset (upk user_id) :token (:token new-token) :facebook_token new-facebook-token)))

(defn create-password-reset-token [email reset-token]
	(wcar redis-conn
		(car/set (reset-email-key reset-token) email)
		(car/set (useremail-reset-key email) reset-token)))

(defn delete-password-reset-token [email reset-token]
	(wcar redis-conn
		(car/del (reset-email-key reset-token) email)
		(car/del (useremail-reset-key email) reset-token)))

(defn retrieve-password-reset-token-by-useremail [email]
	(wcar redis-conn (car/get (useremail-reset-key email))))

(defn retrieve-useremail-by-reset-token [token]
	(wcar redis-conn (car/get (reset-email-key token))))

(defn update-user-password [user_id password]
	(wcar redis-conn (car/hmset* (upk user_id) {:password password})))

(defn update-account-settings [user_id {:keys [email] :as param-map}]
	(wcar redis-conn (car/hmset* (upk user_id) param-map))
	(if email
		(wcar redis-conn (car/set (uek email) user_id))))

(defn assign-facebook-id [user_id facebook_id]
	(wcar redis-conn
		(car/hmset* (upk user_id) {:facebook_id facebook_id})
		(car/set (ufbidk facebook_id) user_id)))


(defn create-bill-meta-data [{:keys [bill_id] :as meta-data}]
  (let [key (str "bill:" bill_id ":meta")]
    (wcar redis-conn
      (car/hmset* key meta-data)
      (car/expire key (* 24 3600)))))

(defn- pageview-bill-key-24hr []
  (str "top-pageviews:bills:" (.toString (.toLocalDate (t/now)))))

(defn- increment-bill-pageview-24hr [key]
  (wcar redis-conn
    (car/zincrby (pageview-bill-key-24hr) 1 key)
    (car/expire key (* 24 3600))))

(defn increment-bill-pageview [{:keys [bill_id] :as bill-meta}]
  (create-bill-meta-data bill-meta)
  (increment-bill-pageview-24hr (str "bill:" bill_id ":meta")))

(defn- retrieve-bill-meta [key]
  (wcar redis-conn (car/parse-map (car/hgetall key) :keywordize)))

(defn retrieve-top-trending-bills-24hr [limit]
  (->> (wcar redis-conn (car/zrevrange (pageview-bill-key-24hr) 0 limit))
       (mapv retrieve-bill-meta)))
