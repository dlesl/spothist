(ns spothist.libs
  (:require [reagent.core :as r]
            [sodium]))

(def sodium-ready (r/atom false))

(defn init-libs! []
  (.then sodium/ready #(reset! sodium-ready true)))
