(ns com.pav.api.test.bill.bill-comment-service-test
  (:use midje.sweet)
  (:require [com.pav.api.test.utils.utils :as u]
            [com.pav.api.dbwrapper.user :as du]
            [com.pav.api.domain.user :refer [assoc-common-attributes]]
            [com.pav.api.services.comments :as service]))

(def test-comment {:bill_id "hr2-114" :body "comment goes here!!"})

(against-background [(before :facts (do (u/flush-dynamo-tables)))]
  (facts  "Test cases for comments caching layer and service interaction"

    (fact "Create new comment, like comment and retrieve comments with correct scores"
      (let [{user_id :user_id :as user} (-> (assoc-common-attributes (u/new-pav-user)) du/create-user)
            {{:keys [comment_id]} :record} (service/create-bill-comment test-comment user)]
        (service/score-bill-comment user_id comment_id :like)
        (first (:comments (service/get-bill-comments user_id "hr2-114"))) =>
          (contains {:liked true :disliked false :score 1})))

    (fact "Create new comment, dislike comment and retrieve comments with correct scores"
      (let [{user_id :user_id :as user} (-> (assoc-common-attributes (u/new-pav-user)) du/create-user)
            {{:keys [comment_id]} :record} (service/create-bill-comment test-comment user)]
        (service/score-bill-comment user_id comment_id :dislike)
        (first (:comments (service/get-bill-comments user_id "hr2-114"))) =>
          (contains {:liked false :disliked true :score -1})))))
