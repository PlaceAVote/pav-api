(ns com.pav.api.schema.user
  (:require [schema.core :as s]
            [clojure.tools.logging :as log])
  (import (java.util Locale)))

(defn retrieve-iso3-country-code [country]
  (-> (Locale. "" country)
      (.getISO3Country)))

(def iso3-codes (into #{} (map retrieve-iso3-country-code (Locale/getISOCountries))))
(def pwd-schema (s/both (s/pred (complement empty?)) s/Str #"^$|^\S.{4,}\S$"))
(def email-schema (s/both (s/pred (complement empty?)) #"^[^@]+@[^@\\.]+[\\.].+"))
(def dob-schema #"^[0-3]?[0-9].[0-3]?[0-9].(?:[0-9]{2})?[0-9]{2}$")
(def county-code-schema (s/both s/Str (s/pred (complement empty?)) (s/pred #(contains? iso3-codes %))))
(def gender-schema (s/both s/Str (s/pred (complement empty?)) (s/enum "male" "female" "they")))
(def str-schema (s/both (s/pred (complement empty?)) s/Str))
(def zip-schema #"^[0-9]{5}")

(def User
  {:email                    email-schema
   :password                 pwd-schema
   :first_name               str-schema
   :last_name                str-schema
   :dob                      dob-schema
   :topics                   [str-schema]
   :gender                   gender-schema
   :zipcode                  zip-schema})

(def FacebookUser
  {:email                    email-schema
   :first_name               str-schema
   :last_name                str-schema
   :dob                      dob-schema
   :topics                   [str-schema]
   :gender                   gender-schema
   :img_url                  str-schema
   :token                    str-schema
   :id                       str-schema
   :zipcode                  zip-schema})

(def UserRecord
	{:email        email-schema
	 :first_name   s/Str
	 :last_name    s/Str
	 :dob          dob-schema
	 :country_code county-code-schema
	 :topics       [s/Str]
	 :token        s/Str})

(def UserLogin
  {:email    email-schema
   :password pwd-schema})

(def FacebookLogin
	{:email    email-schema
	 :id       str-schema
	 :token    str-schema})

(def NewIssuesWithBill
  (s/either
    {:comment                       str-schema
     :bill_id                       str-schema
     (s/optional-key :article_link) str-schema}

    {:bill_id                       str-schema
     :article_link                  str-schema}))

(def NewIssue
  (s/both
    (s/pred (complement empty?))
    {(s/optional-key :comment) str-schema
     (s/optional-key :bill_id) str-schema
     (s/optional-key :article_link) str-schema}))

(def AccountSettingUpdate
	{(s/optional-key :email)      email-schema
	 (s/optional-key :first_name) str-schema
	 (s/optional-key :last_name)  str-schema
	 (s/optional-key :dob)        dob-schema
	 (s/optional-key :public)     s/Bool
	 (s/optional-key :gender)     gender-schema
	 (s/optional-key :city)     	str-schema})

(def ChangePassword
	{:current_password pwd-schema
	 :new_password 		 pwd-schema})

(def ResetPasswordConfirm
	{:new_password pwd-schema
	 :reset_token str-schema})

(def ContactFormEmail
  {:name  str-schema
   :email email-schema
   :body  str-schema})

(defn validate-new-user-payload [user origin]
  (case origin
    :pav (s/check User user)
    :facebook (s/check FacebookUser user)))

(defn validate-login-payload [user origin]
  (case origin
    :pav (s/check UserLogin user)
    :facebook (s/check FacebookLogin user)))

(defn validate-settings-payload [payload]
	(s/check AccountSettingUpdate payload))

(defn validate-change-password-payload [passwords]
	(s/check ChangePassword passwords))

(defn validate-confirm-reset-password-payload [payload]
	(s/check ResetPasswordConfirm payload))

(defn validate-contact-form [payload]
  (s/check ContactFormEmail payload))

(defn validate-new-issue-payload [{:keys [bill_id] :as payload}]
  (if bill_id
    (s/check NewIssuesWithBill payload)
    (s/check NewIssue payload)))

(defn find-suitable-error [[k _]]
  (cond (= :email k) {k "A valid email address is a required"}
        (= :name k)  {k "A valid name is a required"}
        (= :body k)  {k "A valid text body is a required"}
        (= :current_password k) {k "Current Password is a required field"}
        (= :new_password k) {k "New Password is a required field"}
        (= :reset_token k) {k "A valid reset token is required"}
        (= :password k) {k "Password must be a minimum of 6 characters in length."}
        (= :country_code k) {k "Country Code is a required field.  Please Specify Country Code"}
        (= :city k) {k "Please specify a valid city"}
        (= :first_name k) {k "First Name is a required field"}
        (= :last_name k) {k "Last Name is a required field"}
        (= :dob k) {k "Date of birth is a required field"}
        (= :topics k) {k "Please specify a list of topics."}
        (= :token k) {k "A token is required for social media registerations and logins"}
        (= :img_url k) {k "A IMG URL is required for social media registerations and logins"}
				(= :gender k) {k "Please specify a valid gender.  Valid values are male, female and they"}
				(= :id k) {k "Please specify a facebook id"}
				(= :comment k) {k "Please include a comment"}
        (= :zipcode k) {k "A valid 5 digit zipcode code is required for US citizens, e.g 90210"}
				:else {k "field is unknown"}))

(defn construct-error-msg [errors]
  (log/error (str "An Error has occured " errors))
  {:errors (map find-suitable-error errors)})