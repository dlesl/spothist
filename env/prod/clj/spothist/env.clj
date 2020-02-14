(ns spothist.env
  (:require [clojure.tools.logging :as log]))

(def defaults
  {:init
   (fn []
     (log/info "\n-=[spothist started successfully]=-"))
   :stop
   (fn []
     (log/info "\n-=[spothist has shut down successfully]=-"))
   :middleware identity})
