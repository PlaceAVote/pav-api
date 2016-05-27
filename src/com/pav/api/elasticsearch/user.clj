(ns com.pav.api.elasticsearch.user
  (:require [clojurewerkz.elastisch.rest :refer [connect]]
            [clojurewerkz.elastisch.rest.document :as esd]
            [clojurewerkz.elastisch.rest.response :as esrsp]
            [clojurewerkz.elastisch.query :as q]
            [environ.core :refer [env]]
            [taoensso.truss :refer [have]]
            [clojure.tools.logging :as log]
            [clojure.string :as s]
            [com.pav.api.dynamodb.comments :as comments]))

(def connection (connect (:es-url env)))

(defn index-user [user]
  (esd/put connection "pav" "users" (:user_id user) user {:refresh true}))

(defn merge-type-and-fields [hit]
  (merge {:type (:_type hit)} (:_source hit)))

(defn to-pav-subjects [subject]
  (case subject
    "Arts, culture, religion" "Religion"
    "Crime and law Enforcement" "Drugs"
    "Armed forces and national security" "Defense"
    "Government operations and politics" "Politics"
    "Crime and law enforcement" "Gun Rights"
    "Science, technology, communications" "Technology"
    "Economics and public finance" "Economics"
    "Social welfare" "Social Interest"
    subject))

(defn assign-pav-subject [{:keys [type subject] :as hit}]
  (if (= type :bill)
    (update-in hit [subject] to-pav-subjects)
    hit))

(defn get-bills-metadata
  "Retrieve metadata for a collection of bills."
  [bills]
  (->>
    (map #(assoc {} :_id (:bill_id %)) bills)
    (esd/multi-get connection "congress" "billmeta")
    (map :_source)))

(defn get-bill-metadata [bill_id]
  (:_source (esd/get connection "congress" "billmeta" bill_id)))

(defn get-bill-info [bill_id]
  (have string? bill_id)
  (:_source (esd/get connection "congress" "bill" bill_id)))

(defn retrieve-bill-metadata-for-topics [topics]
  (when topics
    (->>
      (esrsp/hits-from
        (esd/search connection "congress" "billmeta"
          :query (q/terms :pav_topic (map clojure.string/lower-case topics))
          :_source [:featured_img_link :govtrack_link :featured_img_links
                    :bill_id :featured_bill_summary :featured_bill_title
                    :pav_topic :points_against :congress :pav_tags :points_infavor]))
      (map :_source))))

(defn search-for-topic
  "Match a users topic selections against pav_topic field on billmeta type"
  [topics]
  (try
    (map (fn [{:keys [bill_id] :as meta}]
           (merge meta {:type "bill"}
             (select-keys (get-bill-info bill_id)
               [:subject :bill_id :official_title :short_title :popular_title :summary])))
      (retrieve-bill-metadata-for-topics topics))
    (catch Exception e (log/error "Error retrieving bills when populating users feed " e))))

(defn search-for-term
  "Search for term across bills and placeavote users."
  [term]
  (->> (esrsp/hits-from (esd/search connection ["congress" "pav"] ["users" "bill"]
                          :query {:multi_match {:query term
                                                :type "best_fields"
                                                :fields ["first_name^2" "last_name^2"
                                                         "keywords" "official_title" "short_title" "popular_title"
                                                         "bill_id" "summary"]
                                                :minimum_should_match "80%"}}
                          :from 0 :size 10
                          :filter {:not {:term {:public false}}}
                          :_source [:first_name :last_name :user_id :img_url
                                    :bill_id :official_title :short_title :popular_title :subject]))
    (mapv merge-type-and-fields)
    (mapv assign-pav-subject)
    (mapv (fn [{:keys [type bill_id] :as record}]
            (case type
              "bill" (merge record (-> bill_id
                                       get-bill-metadata
                                       (select-keys [:featured_bill_title])))
              record)))))

(declare get-bill)

(defn- sanitize-tags-result
  "Use result from searching ES with :pav_tags tag and kick out some fields. Also, find
yes/no votes for this bill."
  [result-mp]
  ;; :event_id, :user_id, :timestamp
  (let [mp (-> result-mp
               :_source
               (select-keys [:featured_img_link :govtrack_link :bill_id
                             :featured_bill_summary :featured_bill_title :pav_topic
                             :points_against :congress :pav_tags :points_infavor]))
        id (:bill_id mp)
        ;; FIXME: prevents cyclic load dependency and should be refactored as possible
        get-vote-count (resolve 'com.pav.api.services.votes/get-vote-count)]
    (merge mp
           {:type :bill
            :comment_count (comments/get-comment-count id)}
           (select-keys (get-bill id) [:short_title :official_title :subject])
           (get-vote-count id))))

(defn search-with-tag
  "Search for bills with given tag or multiple tags separated by comma."
  [tag]
  (when tag
    (let [t (-> tag s/lower-case (s/split #"\s*,\s*"))]
      (->> (esd/search connection "congress" "billmeta" :query (q/term :pav_tags t))
           esrsp/hits-from
           (map sanitize-tags-result)
           (sort-by :comment_count >)
           ;; return vector like other functions
           vec))))

(defn gather-latest-bills-by-subject [topics]
  (some->> topics
           search-for-topic
           (mapv (fn [{:keys [pav_topic] :as bill}]
                   (if pav_topic
                     (assoc bill :subject pav_topic)
                     (update-in bill [:subject] to-pav-subjects))))))

(defn get-legislator [thomas]
  (-> (esd/get connection "congress" "legislator" thomas)
    (get-in [:_source])))

(defn get-bill [bill_id]
  (->>
    (esd/multi-get connection "congress" [{:_type "bill" :_id bill_id} {:_type "billmeta" :_id bill_id}])
    (map :_source) (apply merge)))

(defn get-priority-bill-title [bill-info]
  (when-let [{:keys [official_title short_title featured_bill_title]} bill-info]
    (cond
      featured_bill_title featured_bill_title
      short_title short_title
      official_title official_title)))
