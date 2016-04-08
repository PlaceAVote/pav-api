(ns com.pav.user.api.services.bills
  (:require [com.pav.user.api.elasticsearch.user :as es]
            [com.pav.user.api.services.votes :as vs]
            [com.pav.user.api.services.comments :as cs]
            [com.pav.user.api.redis.redis :as redis]
            [cheshire.core :as ch]
            [clj-http.client :as http]
            [environ.core :refer [env]]))

(def sunlight-api-key (:sunlight-congress-apikey env))

(defn assoc-bill-text [{:keys [bill_type congress number] :as bill}]
  (let [url (format "https://congress.api.sunlightfoundation.com/bills?bill_type=%s&number=%s&congress=%s&apikey=%s"
              bill_type number congress sunlight-api-key)]
    (if-let [last_version (-> (http/get url) :body (ch/parse-string true) :results first :last_version)]
      (assoc bill :last_version last_version)
      bill)))

(defn extract-pageview-metadata [bill]
  (select-keys bill [:bill_id :official_title :short_title :popular_title :summary :subject]))

(defn construct-user-bill-vote [bill_id user_id]
  (let [{vote :vote} (vs/get-votes-for-bill bill_id user_id)]
    (case vote
      true {:voted_for true :voted_against false}
      false {:voted_for false :voted_against true}
      nil {:voted_for false :voted_against false})))

(defn get-bill [bill_id user_id]
  (let [bill (->> (es/get-bill bill_id) assoc-bill-text)]
    (when bill
      (redis/increment-bill-pageview (extract-pageview-metadata bill))
      (if user_id
        (merge bill (construct-user-bill-vote bill_id user_id))
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
    (mapv vs/assoc-bill-vote-count)))