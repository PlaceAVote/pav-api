 (ns system
  (:require [org.httpkit.server :refer [run-server]]
            [pav-user-api.handler :refer [app init]]
            [clojure.tools.logging :as log])
   (:gen-class))

 (defn -main []
   (run-server app {:port 8080})
   (log/info "Server Listening on port 8080"))
