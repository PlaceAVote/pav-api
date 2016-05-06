(ns com.pav.api.services.bills
  (:require [com.pav.api.elasticsearch.user :as es]
            [com.pav.api.services.votes :as vs]
            [com.pav.api.services.comments :as cs]
            [com.pav.api.redis.redis :as redis]
            [com.pav.api.utils.utils :as u]
            [environ.core :refer [env]]))

(def sunlight-api-key (:sunlight-congress-apikey env))

(defn assoc-bill-text [{:keys [bill_type congress number] :as bill}]
  (let [ret (u/http-to-json-sunlightfoundation-api
             (format "/bills?bill_type=%s&number=%s&congress=%s&apikey=%s"
                     bill_type number congress sunlight-api-key))]
    (merge
     bill
     (select-keys ret [:last_version]))))

(defn extract-pageview-metadata [bill]
  (select-keys bill [:bill_id :official_title :short_title :popular_title :summary :subject]))

(defn construct-user-bill-vote [bill_id user_id]
  (if-let [_ (vs/get-votes-for-bill bill_id user_id)]
    {:user_voted true}
    {:user_voted false}))

(defn get-bill [bill_id user_id]
  (let [bill (->> (es/get-bill bill_id) assoc-bill-text)]
    (when bill
      (redis/increment-bill-pageview (extract-pageview-metadata bill))
      (if user_id
        (merge bill (construct-user-bill-vote bill_id user_id) {:comment_count (cs/get-comment-count bill_id)})
        bill))))

(defn assoc-bill-metadata
  "Accepts a collection of bills and retrieves its associated metadata before
  merging contents together."
  [trending-bills]
  (if-let [metadata (->>
                      (es/get-bills-metadata trending-bills)
                      (into {} (map (fn [m] [(:bill_id m) m]))))]
    (map #(merge % (metadata (:bill_id %))) trending-bills)
    trending-bills))

(defn trending-bills []
  (->>
    (redis/retrieve-top-trending-bills-24hr 10)
    assoc-bill-metadata
    (mapv cs/assoc-bill-comment-count)
    (mapv vs/assoc-bill-vote-count)
    (sort-by :comment_count >)))
