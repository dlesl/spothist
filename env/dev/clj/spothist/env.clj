(ns spothist.env
  (:require
   [clojure.tools.logging :as log]
   [spothist.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (log/info "\n-=[spothist started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[spothist has shut down successfully]=-"))
   :middleware wrap-dev})
