(ns com.pav.api.events.comment
  (:require [schema.core :as s])
  (:import (java.util UUID)))

(s/defrecord CommentTimeLineEvent
  [event_id 					:- s/Str
   user_id 	 					:- s/Str
   comment_id 				:- s/Str
   bill_id						:- s/Str
   type 							:- s/Str
   timestamp 					:- Long])

(s/defn ^:always-validate create-comment-timeline-event :- CommentTimeLineEvent
  ([event_id {:keys [author comment_id bill_id timestamp]}]
    (CommentTimeLineEvent. event_id author comment_id bill_id "comment" timestamp))
  ([comment]
    (create-comment-timeline-event (.toString (UUID/randomUUID)) comment)))

(s/defrecord CommentNewsFeedEvent
  [event_id 					:- s/Str
   user_id 	 					:- s/Str
   comment_id 				:- s/Str
   bill_id						:- s/Str
   type 							:- s/Str
   timestamp 					:- Long
   read               :- s/Bool])

(s/defn ^:always-validate create-comment-newsfeed-event :- CommentNewsFeedEvent
  ([event_id {:keys [author comment_id bill_id timestamp]}]
    (CommentNewsFeedEvent. event_id author comment_id bill_id "comment" timestamp false))
  ([comment]
    (create-comment-newsfeed-event (.toString (UUID/randomUUID)) comment)))