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

(s/defrecord CommentScoreTimelineEvent
  [event_id 					:- s/Str
   user_id 	 					:- s/Str
   comment_id 				:- s/Str
   bill_id						:- s/Str
   type 							:- s/Str
   timestamp 					:- Long])

(s/defn ^:always-validate create-comment-score-timeline-event :- CommentScoreTimelineEvent
  ([event_id operation {:keys [author comment_id bill_id timestamp]}]
    (let [type (case operation
                 :like    "likecomment"
                 :dislike "dislikecomment")]
      (CommentScoreTimelineEvent. event_id author comment_id bill_id type timestamp)))
  ([operation comment]
    (create-comment-score-timeline-event (.toString (UUID/randomUUID)) operation comment)))

(s/defrecord CommentReplyNotificationEvent
  [notification_id  :- s/Str
   user_id 	 				:- s/Str
   comment_id 			:- s/Str
   parent_id        :- s/Str
   bill_id					:- s/Str
   type 						:- s/Str
   timestamp 				:- Long
   read             :- s/Bool])

(s/defn ^:always-validate create-comment-reply-notification-event :- CommentReplyNotificationEvent
  ([notification_id {:keys [author comment_id parent_id bill_id timestamp]}]
    (CommentReplyNotificationEvent. notification_id author comment_id parent_id bill_id "commentreply" timestamp false))
  ([comment]
    (create-comment-reply-notification-event (.toString (UUID/randomUUID)) comment)))