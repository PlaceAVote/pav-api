(ns com.pav.api.resources.reports
  (:require [liberator.core :refer [resource defresource]]
            [com.pav.api.services.reporting :as reports]))

(defresource activity-report [weeks]
  :service-available? {:representation {:media-type "application/json"}}
  :allowed-methods [:get]
  :available-media-types ["application/json"]
  :exists? (fn [_] {:record (reports/generate-csv-report-n-wks weeks)})
  :handle-ok :record)


