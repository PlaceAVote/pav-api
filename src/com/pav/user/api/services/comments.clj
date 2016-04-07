(ns com.pav.user.api.services.comments
  (:require [com.pav.user.api.dynamodb.comments :as dc]))

(defn add-bill-comment-count [comment]
  (dc/add-bill-comment-count comment))
