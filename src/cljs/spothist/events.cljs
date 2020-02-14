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
 (fn [_ [_ result]]
   (js/alert (str result))
   (println result)))

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
 (fn [{:keys [db]} _]
   (if (and (:keypair db) (-> db :status :registered?))
     {:dispatch [::get-events nil]}
     {::effects/sql-init! nil
      :db (assoc db :data-fetched? nil)})))

(rf/reg-event-fx
 ::get-events
 (fn [{:keys [db]} [_ start-at]]
   (merge
    {:http-xhrio
     {:method :GET
      :uri "/api/events_bin"
      :params (when start-at {:start_at start-at})
      :response-format {:read -body :type :arraybuffer}
      :on-success [::got-events]
      :on-failure [::log]}}
    ;; reset everything if this is the first request
    (if-not start-at
      {::effects/sql-init! nil
       :db (assoc db
                  :data-fetched? false
                  :data-fetch-error nil
                  :data-fetch-progress 0
                  :query-result nil
                  :query-error nil)}))))

(rf/reg-event-fx
 ::got-events
 (fn [{:keys [db]} [_ result]]
   (try
     (let [payloads (payload/read-records (js/DataView. result))]
       (if (seq payloads)
         (let [events (crypto/decode payloads (:keypair db))
               start-at (:added_at (last payloads))]
           {::effects/sql-insert! {:events events
                                   :on-failure [::log]}
            :db (update db :data-fetch-progress (fnil + 0) (count events))
            :dispatch [::get-events start-at]})
         {:db (assoc db
                     :data-fetched? true
                     :data-fetch-progress nil)
          ::effects/sql-get-schema [::got-schema]}))
     (catch js/Error e
       {:db (assoc db
                   :data-fetch-error e
                   :data-fetch-progress nil)
        :dispatch [::log e]}))))

(rf/reg-event-fx
 ::eval-sql
 (fn [_ [_ text]]
   {::effects/sql-query!
    {:query text
     :on-success [::got-query-result]
     :on-failure [::got-query-error]}}))

(rf/reg-event-fx
 ::export-sql
 (fn [_ _]
   {::effects/sql-export! nil}))

(rf/reg-event-fx
 ::got-query-result
 (fn [{:keys [db]} [_ rows]]
   {:db
    (assoc db
           :query-result rows
           :query-error nil)
    ::effects/sql-get-schema [::got-schema]}))

(rf/reg-event-db
 ::got-query-error
 (fn [db [_ e]]
   (assoc db
          :query-result nil
          :query-error e)))

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

(rf/reg-event-fx
 ::load-demo-data
 (fn [_ [_ _]]
   {:http-xhrio
    {:method :GET
     :uri "/demo.db"
     :response-format {:read -body :type :arraybuffer}
     :on-success [::got-demo-data]
     :on-failure [::log]}}))

(rf/reg-event-fx
 ::got-demo-data
 (fn [{db :db} [_ result]]
   {::effects/sql-init! result
    ::effects/sql-get-schema [::got-schema]
    :db (assoc db
               :data-fetched? true
               :data-fetch-progress nil)}))
