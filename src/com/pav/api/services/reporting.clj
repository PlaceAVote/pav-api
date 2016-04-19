(ns com.pav.api.services.reporting
  (:require [clj-time.core :as t]
            [clj-time.periodic :as p]
            [clj-time.coerce :as c]
            [clj-time.format :as f]
            [com.pav.api.dynamodb.comments :as dc]
            [com.pav.api.dynamodb.votes :as dv]
            [com.pav.api.dynamodb.user :as du]))

(def date-key-formatter (f/formatters :date))

(defn days-for-n-wks-ago [weeks]
  (->> (p/periodic-seq
         (t/minus (t/today-at-midnight) (t/weeks weeks))
         (t/now)
         (t/days 1))
       (map #(-> (.toDateTime %)))))

(defn- get-counts-for-date [date]
  (let [start (c/to-long date)
        end (c/to-long (t/plus date (t/hours 24)))]
    {:date          (f/unparse date-key-formatter date)
     :comment_count (dc/comment-count-between start end)
     :vote_count    (dv/votes-count-between start end)
     :signup_count    (du/user-count-between start end)}))

(defn- activity-report-for-n-wks [wks]
  (when (number? wks)
    (let [data (map get-counts-for-date (days-for-n-wks-ago wks))]
      {:results        data
       :total_signups  (reduce + (map :signup_count data))
       :total_comments (reduce + (map :comment_count data))
       :total_votes    (reduce + (map :vote_count data))})))

(defn generate-csv-report-n-wks [wks]
  (if (seq wks)
    (activity-report-for-n-wks (read-string wks))))