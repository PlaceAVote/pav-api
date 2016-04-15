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
   author             :- s/Str
   comment_id 				:- s/Str
   bill_id						:- s/Str
   type 							:- s/Str
   timestamp 					:- Long])

(s/defn ^:always-validate create-comment-score-timeline-event :- CommentScoreTimelineEvent
  ([event_id operation scorer_id {:keys [author comment_id bill_id timestamp]}]
    (let [type (case operation
                 :like    "likecomment"
                 :dislike "dislikecomment")]
      (CommentScoreTimelineEvent. event_id scorer_id author comment_id bill_id type timestamp)))
  ([operation scorer_id comment]
    (create-comment-score-timeline-event (.toString (UUID/randomUUID)) operation scorer_id comment)))

(s/defrecord CommentReplyNotificationEvent
  [notification_id  :- s/Str
   user_id 	 				:- s/Str
   author 	 				:- s/Str
   comment_id 			:- s/Str
   parent_id        :- s/Str
   bill_id					:- s/Str
   type 						:- s/Str
   timestamp 				:- Long
   read             :- s/Bool])

(s/defn ^:always-validate create-comment-reply-notification-event :- CommentReplyNotificationEvent
  ([notification_id {:keys [user_id author comment_id parent_id bill_id timestamp]}]
    (CommentReplyNotificationEvent. notification_id user_id author comment_id parent_id bill_id "commentreply" timestamp false))
  ([comment]
    (create-comment-reply-notification-event (.toString (UUID/randomUUID)) comment)))

(s/defrecord CommentReplyWSNotificationEvent
  [notification_id   :- s/Str
   user_id 	 				 :- s/Str
   comment_id 			 :- s/Str
   parent_id         :- s/Str
   bill_id					 :- s/Str
   bill_title 			 :- s/Str
   author            :- s/Str
   author_first_name :- s/Str
   author_last_name  :- s/Str
   author_img_url    :- (s/maybe s/Str)
   body              :- s/Str
   type 						 :- s/Str
   timestamp 				 :- Long
   read              :- s/Bool])

(s/defn create-comment-reply-wsnotification-event :- CommentReplyWSNotificationEvent
  ([notification_id {:keys [user_id comment_id parent_id bill_id bill_title body
                            author author_first_name author_last_name author_img_url timestamp]}]
    (CommentReplyWSNotificationEvent.
      notification_id user_id comment_id parent_id bill_id bill_title
      author author_first_name author_last_name author_img_url
      body "commentreply" timestamp false))
  ([comment]
    (create-comment-reply-wsnotification-event (.toString (UUID/randomUUID)) comment)))

(s/defrecord CommentReplyEmailNotificationEvent
  [comment_id        :- s/Str
   bill_id					 :- s/Str
   bill_title 			 :- s/Str
   author            :- s/Str
   author_first_name :- s/Str
   author_last_name  :- s/Str
   author_img_url    :- (s/maybe s/Str)
   body              :- s/Str
   email             :- s/Str])

(s/defn create-comment-reply-email-notification-event :- CommentReplyEmailNotificationEvent
  [{:keys [comment_id email bill_id bill_title body author author_first_name author_last_name author_img_url]}]
    (CommentReplyEmailNotificationEvent. comment_id bill_id bill_title
      author author_first_name author_last_name author_img_url body email))