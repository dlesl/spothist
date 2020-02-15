(ns spothist.libs
  (:require [reagent.core :as r]
            [sodium]))

(def sodium-ready (r/atom false))

(def sql-ready (r/atom false))

(defn init-libs! []
  (.then sodium/ready #(reset! sodium-ready true))
  (.then js/sqlReady #(reset! sql-ready true)))
