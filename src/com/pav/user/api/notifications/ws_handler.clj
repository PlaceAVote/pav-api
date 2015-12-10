(ns com.pav.user.api.notifications.ws-handler
	(:require [org.httpkit.server :refer [send! with-channel on-close on-receive close]]
						[clojure.tools.logging :as log]
						[com.pav.user.api.utils.utils :refer [retrieve-user-id unpack-redis-msg to-json]]
						[com.pav.user.api.authentication.authentication :as auth]
						[com.pav.user.api.redis.redis :refer [redis-conn]]
						[taoensso.carmine :as car]
						[environ.core :refer [env]]))

;; Holds all currently connected users to this node.
(defonce channels (atom []))

;;Redis Notification Topic
(def redis-notification-pubsub (:redis-notification-pubsub env))

(defn notify-client [[_ _ msg]]
	"When a Notification is received from the redis pub/sub.  Iterate through current channels, if the user_id on the
	notification matches a user_id and channel combination, send the notification on the channel"
	(if-not (= Long (type msg))
		(try
			(let [m (unpack-redis-msg msg)
						clients (filter #(= (:user_id m) (:user_id %)) @channels)]
			 (doseq [client clients]
				 (send! (:channel client) (to-json m))))
		(catch Exception e (log/error "Exception occured processing notification from redis pub/sub, MESSAGE: " msg ", ERROR: " e)))))

(defn start-notification-listener []
	(log/info "Starting Notification Pub/Sub Listener")
	(try
		(car/with-new-pubsub-listener redis-conn
		 {redis-notification-pubsub notify-client}
		 (car/subscribe redis-notification-pubsub))
		(log/info "Started Notification Pub/Sub Listener")
		(catch Exception e (log/error "Error occured starting Notification Pub/Sub Listener for " redis-conn
												 " on topic" redis-notification-pubsub e))))

(defn connect! [{:keys [user_id]} channel]
	(log/info "Channel Open for " user_id)
	(swap! channels conj {:user_id user_id :channel channel}))

(defn disconnect! [channel {:keys [code reason]}]
	(log/info "Channel closed, close code: " code " reason: " reason)
	(swap! channels (fn [col]
										(filter #(not (= channel (:channel %))) col))))

(defn notify-clients [msg]
	(log/info "Message received from client, Not Interested " msg))

(defn ws-notification-handler [{:keys [params] :as request}]
	(let [token (:token params)]
		(with-channel request channel
			(on-close channel (partial disconnect! channel))
			(on-receive channel #(notify-clients %))
			(if (auth/token-valid? token)
				(connect! (auth/unpack-token token) channel)
				(close channel)))))


