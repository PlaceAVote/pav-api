 (ns system
  (:require [pav-user-api.handler :refer [app start-server]]
            [clojure.tools.logging :as log])
   (:gen-class))

 (defn -main []
   (start-server {:port 8080})
   (log/info "Server Listening on port 8080"))
