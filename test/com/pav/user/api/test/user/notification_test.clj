(ns com.pav.user.api.test.user.notification-test
	(:use midje.sweet)
	(:require [com.pav.user.api.test.utils.utils :refer [pav-req]]))

(against-background []
	(fact "Make a websocket connection to the websocket notification endpoint"))
