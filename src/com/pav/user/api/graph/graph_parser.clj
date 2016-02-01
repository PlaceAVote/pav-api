(ns com.pav.user.api.graph.graph-parser
  (:require [net.cgrand.enlive-html :as html]
            [clojure.tools.logging :as log])
  (:import (java.net URL)))

(defn- fetch-url [url]
  (with-open [stream (-> (URL. url)
                         .openConnection
                         (doto (.setRequestProperty "User-Agent"
                                 "Mozilla/5.0"))
                         .getContent)]
    (html/html-resource stream)))

(defn- filter-props [meta-tags props]
  (filterv #(contains? props (get-in % [:attrs :property])) meta-tags))

(defn- tag-name [prop-name]
  (case prop-name
    "og:title"  :article_title
    "og:url"    :article_link
    "og:image"  :article_img))

(defn- merge-graph-content [tags]
  (when tags
    (->> (map (fn [tag]
               (let [content (get-in tag [:attrs :content])
                     key     (tag-name (get-in tag [:attrs :property]))]
                 {key content})) tags)
         (reduce merge))))

(defn extract-open-graph [url]
  "Extract Open Graph data from page if it exists, else return nil"
  (try
    (-> (fetch-url url)
        (html/select [:head :meta])
        (filter-props #{"og:url" "og:title" "og:image"})
        merge-graph-content)
  (catch Exception e (log/warn (str "Cannot extract open graph data from " url ", error: " e)))))
