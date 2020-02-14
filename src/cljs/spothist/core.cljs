(ns spothist.core
  (:require
   [reagent.core :as r]
   [re-frame.core :as rf]
   [reitit.frontend.easy :as rfe]
   [spothist.events :as events]
   [spothist.routes :as routes]
   [spothist.effects]
   [spothist.views :as views]
   [sodium]))

(defn mount-components []
  (rf/clear-subscription-cache!)
  (r/render [views/main-panel]
            (.getElementById js/document "app")))

(defn ^:export init! []
  (enable-console-print!)
  ;; we need to wait for libsodium and SQL.js to load
  ;; before continuing
  (.then js/window.jsInit               ;; defined in src/js/index.js
         (fn []
           (rf/dispatch-sync [::events/initialize-db])
           (rf/dispatch [::events/get-status])
           (rf/dispatch [::events/restore-keypair])
           (rfe/start!
            routes/router
            #(rf/dispatch [::events/navigated %]) {:use-fragment false})
           (mount-components))))
