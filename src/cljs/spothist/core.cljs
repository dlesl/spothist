(ns spothist.core
  (:require
   [reagent.core :as r]
   [re-frame.core :as rf]
   [reitit.frontend.easy :as rfe]
   [spothist.events :as events]
   [spothist.routes :as routes]
   [spothist.effects]
   [spothist.views :as views]
   [spothist.libs :refer [init-libs!]]
   [sodium]))

(defn mount-components []
  (rf/clear-subscription-cache!)
  (r/render [views/main-panel]
            (.getElementById js/document "app")))

(defn ^:export init! []
  (enable-console-print!)

  ;; Some compontents need SQLite and libsodium to be ready before
  ;; mounting. WASM can only be loaded asynchronously...

  ;; When adding anything that runs automatically on startup, make
  ;; sure that it doesn't rely on these libraries!

  ;; This is not the most elegant solution but the main alternatives I
  ;; can think of are:
  ;; - Waiting for everything before mounting anything
  ;; - Making everything that touches these libs async (this + running
  ;; SQLite in a worker might be the best option in the future as data
  ;; sets get larger)

  (init-libs!)

  (rf/dispatch-sync [::events/initialize-db])
  (rf/dispatch [::events/get-status])
  ;; (rf/dispatch [::events/restore-keypair])
  (rfe/start!
   routes/router
   #(rf/dispatch [::events/navigated %]) {:use-fragment false})
  (mount-components))
