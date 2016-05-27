(ns com.pav.api.test.bill.bill-comment-service-test
  (:use midje.sweet)
  (:require [com.pav.api.test.utils.utils :as u]
            [com.pav.api.dbwrapper.user :as du]
            [com.pav.api.domain.user :refer [assoc-common-attributes]]
            [com.pav.api.services.comments :as service]))

(def test-comment {:bill_id "hr2-114" :body "comment goes here!!"})

(against-background [(before :facts (do (u/flush-dynamo-tables)))]
  (facts "Test cases for comments caching layer and service interaction"

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
        (contains {:liked false :disliked true :score -1})))

    (fact "Given a new comment, when creating new comment, Then ensure response contains any replies and user metadata"
      (let [user (-> (assoc-common-attributes (u/new-pav-user)) du/create-user)
            {record :record} (service/create-bill-comment test-comment user)]
        record => (just {:author    anything :author_first_name anything :author_last_name anything :author_img_url anything
                         :bill_id   anything :body anything :comment_id anything :has_children false :id anything
                         :parent_id nil :score 0 :timestamp anything :liked false :disliked false :replies [] :deleted false} :in-any-order)))

    (fact "Given a new comment, When replying to the given comment, Then ensure reply response contains any replies and user metadata"
      (let [user (-> (assoc-common-attributes (u/new-pav-user)) du/create-user)
            {{:keys [comment_id]} :record} (service/create-bill-comment test-comment user)
            {record :record} (service/create-bill-comment comment_id test-comment user)]
        record => (just {:author    anything :author_first_name anything :author_last_name anything :author_img_url anything
                         :bill_id   anything :body anything :comment_id anything :has_children false :id anything
                         :parent_id comment_id :score 0 :timestamp anything :liked false :disliked false :replies [] :deleted false} :in-any-order)))))
