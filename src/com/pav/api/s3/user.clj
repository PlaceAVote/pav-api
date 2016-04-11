(ns com.pav.api.s3.user
	(:use [amazonica.aws.s3 :refer [put-object]]
				[clojure.tools.logging :as log]
				[environ.core :refer [env]]
        [clojure.core.async :refer [go]]))

(def creds {:access-key (:access-key env)
						:secret-key (:secret-key env)
						:endpoint (:s3-region env)})

(def s3-upload-allowed? (:s3-upload-allowed env))

(defn upload-to-s3 [bucket key metadata stream]
  (when (= s3-upload-allowed? "true")
    (go
     (put-object creds
       :bucket-name bucket
       :key key
       :metadata metadata
       :input-stream stream)
     (log/info (str "File uploaded for " key)))))

(defn upload-user-profile-image [bucket key file]
	(let [size (file :size)
				content-type (file :content-type)
				actual-file (file :tempfile)]
    (log/info (str "Attempting image upload to " bucket " for " key " of size " size))
    (upload-to-s3 bucket key {:content-type content-type :content-length size} actual-file)))

(defn upload-issue-img [bucket key stream content-type]
  (log/info (str "Attempting image upload to " bucket " for " key))
  (upload-to-s3 bucket key {:content-type content-type} stream))