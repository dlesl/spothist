(ns spothist.effects
  (:require [re-frame.core :as rf]
            [reitit.frontend.easy :as rfe]
            [spothist.sqlite :as sqlite]
            [cognitect.transit :as t]))

(rf/reg-fx
 ::localstorage
 (fn [{:keys [action id event-id data]}]
   (condp = action
     :get (rf/dispatch [event-id (if-let [json (.getItem js/localStorage (name id))]
                                   (t/read (t/reader :json) json))])
     :set (.setItem js/localStorage (name id) (t/write (t/writer :json) data)))))

(rf/reg-fx
 ::navigate!
 (fn [route]
   (apply rfe/push-state route)))

(rf/reg-fx
 ::redirect!
 (fn [url]
   (set! (.-location js/window) url)))

;; sql stuff

(def sql (atom nil))

(rf/reg-fx
 ::sql-init!
 (fn [data]
   (swap! sql (fn [old]
                (when old (sqlite/close old))
                (sqlite/create data)))))

(rf/reg-fx
 ::sql-insert!
 (fn [{:keys [on-failure events]}]
   (try
     (sqlite/insert-plays @sql events)
     (catch js/Error e
       (rf/dispatch (conj on-failure e))))))

(rf/reg-fx
 ::sql-query!
 (fn [{:keys [on-success on-failure query]}]
   (try
     (let [rows (sqlite/query @sql query)]
       (rf/dispatch (conj on-success rows)))
     (catch js/Error e
       (rf/dispatch (conj on-failure e))))))

(rf/reg-fx
 ::sql-get-schema
 (fn [on-success]
   (try
     (let [schema (sqlite/get-schema @sql)]
       (rf/dispatch (conj on-success schema)))
     (catch js/Error e
       (println "failed getting schema " e)))))

(rf/reg-fx
 ::sql-export!
 (fn [_]
   (-> (sqlite/export @sql)
       (array)
       (js/Blob. (clj->js {:type "application/x-sqlite3"}))
       (js/saveAs "sqlite.db")               ;; see src/js/index.js
       )))
