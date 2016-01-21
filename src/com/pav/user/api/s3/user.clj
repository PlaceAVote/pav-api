(ns com.pav.user.api.s3.user
	(:use [amazonica.aws.s3]
				[clojure.tools.logging :as log]
				[environ.core :refer [env]]))

(def creds {:access-key (:access-key env)
						:secret-key (:secret-key env)
						:endpoint (:s3-region env)})

(defn upload-image [bucket key file]
	(let [size (file :size)
				content-type (file :content-type)
				actual-file (file :tempfile)]
		(put-object creds
			:bucket-name bucket
			:key key
		 :metadata {:content-length size
								:content-type content-type}
		 :file actual-file)
		(log/info (str "Image Uploaded for " key))))