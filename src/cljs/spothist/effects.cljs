(ns spothist.effects
  (:require [re-frame.core :as rf]
            [reitit.frontend.easy :as rfe]
            [spothist.sql :as sql]
            [spothist.payload :as payload]
            [spothist.crypto :as crypto]
            [cognitect.transit :as t]
            [promesa.core :as p]
            [ajax.core :refer [GET]]
            [ajax.protocols :refer [-body]]))

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
 (fn [{:keys [demo? keypair data on-success on-progress on-failure]}]
   (swap! sql (fn [old]
                (when old (sql/close old))
                (sql/sql-worker)))
   (println "in load")
   (if demo?
     (-> (p/create
          (fn [resolve reject]
            (GET "/demo.db"
                 {:response-format {:read -body :type :arraybuffer}
                  :handler resolve
                  :error-handler reject})))
         (p/then #(sql/open @sql %))
         (p/then #(rf/dispatch on-success))
         (p/catch #(rf/dispatch (conj on-failure %))))
     (-> (p/do! (sql/open @sql nil)
                (p/loop [start-at nil progress 0]
                  (p/let
                      [data (p/create
                             (fn [resolve reject]
                               (GET "/api/events_bin"
                                    {:params (when start-at {:start_at start-at})
                                     :response-format {:read -body :type :arraybuffer}
                                     :handler resolve
                                     :error-handler reject})))
                       payloads (payload/read-records (js/DataView. data))
                       _ (and
                          (seq payloads)
                          (sql/insert-plays @sql (crypto/decode payloads keypair)))]
                    (when-let [start-at (:added_at (last payloads))]
                      (let [progress (+ progress (count payloads))]
                        (rf/dispatch (conj on-progress (str "loaded " progress " chunks")))
                        (p/recur start-at progress)))))
                (rf/dispatch on-success))
         (p/catch #(rf/dispatch (conj on-failure %)))))))

(rf/reg-fx
 ::sql-request!
 (fn [{:keys [action data on-success on-failure]}]
   (-> (case action
         :insert-plays (sql/insert-plays @sql data)
         :query (sql/query @sql data)
         :get-schema (sql/get-schema @sql))
       (p/then #(when on-success (rf/dispatch (conj on-success %))))
       (p/catch #(rf/dispatch (conj on-failure %))))))

(rf/reg-fx
 ::sql-export!
 (fn [_]
   (p/let [data (sql/export @sql)]
     (-> data
         (array)
         (js/Blob. (clj->js {:type "application/x-sqlite3"}))
         (js/saveAs "sqlite.db"))               ;; see src/js/index.js
     )))
