(ns com.pav.user.api.services.questions
	(:require [com.pav.user.api.dynamodb.user :as dynamo-dao]
						[com.pav.user.api.services.users :refer [get-user-by-id]]))

(defn create-question [question]
	(dynamo-dao/create-question question))

(defn retrieve-questions [user_id]
	(let [{topics :topics} (get-user-by-id user_id)]
		(dynamo-dao/retrieve-questions-by-topics topics)))

(defn submit-answers [user_id answers]
	(if (seq answers)
		(dynamo-dao/submit-answers (map #(assoc % :user_id user_id) (:answers answers)))))
