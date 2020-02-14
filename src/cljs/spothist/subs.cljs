(ns spothist.subs
  (:require
   [re-frame.core :as rf]
   [clojure.string :as str]
   [sodium]))

(rf/reg-sub
 ::status
 (fn [db _]
   (:status db)))

(rf/reg-sub
 ::registered?
 (fn [db _]
   (-> db :status :registered?)))

(rf/reg-sub
 ::authenticated?
 (fn [db _]
   (-> db :status :authenticated?)))

(rf/reg-sub
 ::keypair
 (fn [{:keys [keypair]} _]
   keypair))

(rf/reg-sub
 ::query-result
 (fn [{:keys [query-result]} _]
   query-result))

(rf/reg-sub
 ::query-error
 (fn [{:keys [query-error]} _]
   query-error))

(rf/reg-sub
 ::loading-events
 (fn [{:keys [data-fetch-progress]} _]
   data-fetch-progress))

(rf/reg-sub
 ::events-loaded?
 (fn [db _]
   (:data-fetched? db)))

(rf/reg-sub
 ::current-route
 (fn [db]
   (:current-route db)))

(rf/reg-sub
 ::schema
 (fn [db]
   (:data-schema db)))
