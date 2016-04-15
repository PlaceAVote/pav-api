(ns com.pav.api.events.user
  (:require [schema.core :as s])
  (:import (java.util UUID)))

(s/defrecord FollowingUserTimelineEvent
  [event_id 					:- s/Str
   type 							:- s/Str
   following_id				:- s/Str
   user_id 	 					:- s/Str
   timestamp 					:- Long])

(s/defn ^:always-validate create-followinguser-timeline-event :- FollowingUserTimelineEvent
  ([event_id {:keys [user_id following_id timestamp]}]
    (FollowingUserTimelineEvent. event_id "followinguser" following_id user_id timestamp))
  ([event] (create-followinguser-timeline-event (.toString (UUID/randomUUID)) event)))