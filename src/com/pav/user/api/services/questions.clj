(ns com.pav.user.api.services.questions
	(:require [com.pav.user.api.dynamodb.user :as dynamo-dao]
						[com.pav.user.api.services.users :refer [get-user-by-id]]
						[clojure.tools.logging :as log]
						[taoensso.faraday :as far]))

(defn bootstrap-wizard-questions [questions]
	(log/info (str "Bootstrapping " (count questions) " Wizard Questions"))
	(dynamo-dao/bootstrap-wizard-questions questions))

(defn retrieve-questions [user_id]
	(let [{topics :topics} (get-user-by-id user_id)]
		(dynamo-dao/retrieve-questions-by-topics topics)))

(defn submit-answers [user_id answers]
	(if (seq answers)
		(dynamo-dao/submit-answers
      (map #(assoc % :user_id user_id :answer (far/freeze (:answer %))) (:answers answers)))))
