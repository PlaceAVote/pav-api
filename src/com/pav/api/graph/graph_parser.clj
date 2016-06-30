(ns com.pav.api.graph.graph-parser
  (:require [net.cgrand.enlive-html :as html]
            [clojure.tools.logging :as log])
  (:import (java.net URL)))

(defn- fetch-url [url]
  (with-open [stream (-> url
                         URL.
                         .openConnection
                         (doto (.setRequestProperty "User-Agent"
                                                    "Mozilla/5.0 AppleWebKit/537.36 (KHTML, like Gecko) Chromium/45.0.2454.101 Chrome/45.0.2454.101 Safari/537.36"))
                         .getContent)]
    (html/html-resource stream)))

(defn- filter-props [meta-tags props]
  (filterv #(contains? props (get-in % [:attrs :property])) meta-tags))

(defn- tag-name [prop-name]
  (case prop-name
    "og:title"  :article_title
    "og:url"    :article_link
    "og:image"  :article_img
    (assert false
            (str "Found unexpected property name: " prop-name))))

(defn- merge-graph-content
  "Extract properties and convert them to keys using 'tag-name'. Do the same for content,
except it's value is used as is."
  [tags]
  (reduce (fn [coll mp]
            (assoc coll
              (tag-name (get-in mp [:attrs :property]))
              (get-in mp [:attrs :content])))
          nil
          tags))

(defn- extract-open-graph-only
  "Try to extract only Open Graph data."
  [body]
  (-> body
      (html/select [:head :meta])
      (filter-props #{"og:url" "og:title" "og:image"})
      merge-graph-content))

(defn- fallback-image-url
  "Retrieve fallback image url. First will contact placeavote.com to retrieve default
image from OG data and if that fails, fallback to something really hardcoded.

This approach may sound weird, but when designers update the logo, consumers (e.g. mobile apps)
will get it."
  []
  (if-let [url (-> "https://placeavote.com" fetch-url extract-open-graph-only :article_img)]
    url
    "http://s29.postimg.org/v86d7ur2v/og_fb_img.jpg"))

(defn- fallback-open-graph-data
  "Return alternative to Open Graph data, so we can have some values."
  [body url]
  {:article_link url
   :article_title (-> body
                      (html/select [:head :title])
                      first
                      :content
                      first)
   :article_img (fallback-image-url)})

(defn extract-open-graph
  "Extract Open Graph data and return nil if fails or there isn't any data. If 'fallback?' was
set to true, returns page title as :article_title, page url as :article_link and hardcoded
value for :article_img. However, if url does not exists, it will return nil always."
  ([url fallback?]
     (try
       (let [body (fetch-url url)]
         (if-let [ret (extract-open-graph-only body)]
           ret
           (when fallback?
             (fallback-open-graph-data body url))))
       (catch Exception e
         (log/warn "Cannot extract open graph data from " url ", error: " e))))
  ([url] (extract-open-graph url false)))
