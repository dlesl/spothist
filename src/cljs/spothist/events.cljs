(ns spothist.events
  (:require
   [re-frame.core :as rf]
   [spothist.db :as db]
   [spothist.crypto :as crypto]
   [spothist.payload :as payload]
   [spothist.effects :as effects]
   [day8.re-frame.http-fx]
   [ajax.core :as ajax]
   [ajax.protocols :refer [-body]]
   [sodium]))

(rf/reg-event-db
 ::initialize-db
 (fn [_ _]
   db/default-db))

(rf/reg-event-fx
 ::log
 (fn [{:keys [db]} [_ result]]
   (js/alert (str result))
   (println result)
   {:db (assoc db :query-running? false)})) ;; TODO: proper error handling

(rf/reg-event-fx
 ::get-status
 (fn [_ _]
   {:http-xhrio
    {:method :GET
     :uri "/api/status"
     :response-format (ajax/transit-response-format)
     :on-success [::got-status]
     :on-failure [::log]}}))

(rf/reg-event-fx
 ::got-status
 (fn [{:keys [db]} [_ result]]
   {:db
    (assoc db :status result)
    ;; redo navigation with our new status
    :dispatch-later
    [{:ms 0 :dispatch [::navigated (:current-route db)]}]}))

(rf/reg-event-fx
 ::set-keypair
 (fn [{db :db} [_ keypair]]
   {:db (assoc db :keypair keypair)
    ;; ::effects/localstorage
    ;; {:action :set
    ;;  :id :keypair
    ;;  :data keypair}
    }))

(rf/reg-event-fx
 ::restore-keypair
 (fn [_ _]
   ;; {::effects/localstorage
   ;;  {:action :get
   ;;   :id :keypair
   ;;   :event-id ::set-keypair}}
   ))

(rf/reg-event-fx
 ::register
 (fn [{:keys [db]} _]
   {:http-xhrio
    {:method :POST
     :uri "/api/register"
     :params {:public_key (get-in db [:keypair :public-key])}
     :format (ajax/transit-request-format)
     :response-format (ajax/transit-response-format)
     :on-success [::registered]
     :on-failure [::log]}}))

(rf/reg-event-fx
 ::registered
 (fn [{:keys [db]} _]
   {:db db
    :dispatch [::get-status]}))

(rf/reg-event-fx
 ::reset-sql
 (fn [{:keys [db]} [_ demo?]]
   (if (or demo?
           (and (:keypair db) (-> db :status :registered?)))
     {::effects/sql-init! {:demo? demo?
                           :keypair (:keypair db)
                           :on-success [::got-events]
                           :on-progress [::progress]
                           :on-failure [::log]}
      :db (assoc db
                 :data-fetched? nil
                 :data-fetch-progress "Loading SQLite..."
                 :data-fetch-error nil)}
     {:db (assoc db
                 :data-fetched? nil
                 :data-fetch-progress nil
                 :data-fetch-error nil)})))

(rf/reg-event-fx
 ::got-events
 (fn [{:keys [db]} _]
   {:db (assoc db
               :data-fetched? true
               :data-fetch-progress nil)
    :dispatch [::get-schema]}))

(rf/reg-event-db
 ::progress
 (fn [db [_ progress]]
   (assoc db
          :data-fetch-progress progress)))

(rf/reg-event-fx
 ::eval-sql
 (fn [{:keys [db]} [_ text]]
   {:db (assoc db :query-running? true)
    ::effects/sql-request!
    {:action :query
     :data text
     :on-success [::got-query-result]
     :on-failure [::log]}}))

(rf/reg-event-fx
 ::export-sql
 (fn [_ _]
   {::effects/sql-export! nil}))

(rf/reg-event-fx
 ::got-query-result
 (fn [{:keys [db]} [_ rows]]
   {:db
    (assoc db
           :query-running? false
           :query-result rows
           :query-error nil)
    :dispatch [::get-schema]}))

(rf/reg-event-db
 ::got-query-error
 (fn [db [_ e]]
   (assoc db
          :query-result nil
          :query-error e)))

(rf/reg-event-fx
 ::get-schema
 (fn [{:keys [db]} _]
   {::effects/sql-request! {:action :get-schema
                            :on-success [::got-schema]
                            :on-failure [::log]}}))

(rf/reg-event-db
 ::got-schema
 (fn [db [_ schema]]
   (assoc db :data-schema schema)))

(rf/reg-event-fx
 ::navigate
 (fn [_ [_ & route]]
   {::effects/navigate! route}))

(rf/reg-event-fx
 ::navigated
 (fn [{:keys [db]} [_ new-match]]
   (merge {:db (assoc db :current-route new-match)}
          ;; (when (and
          ;;        (-> new-match :data :data?)
          ;;        (not (some db [:data-fetched? :data-fetch-progress :data-fetch-error]))
          ;;        (-> db :status :registered?)
          ;;        (-> db :keypair))
          ;;   {:dispatch [::get-events nil]})
          )))
