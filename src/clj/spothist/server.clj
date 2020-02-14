(ns spothist.server
  (:require [clojure.core.async :as a]
            [clojure.java.io]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [ring.util.response :refer
             [response redirect bad-request resource-response content-type]]
            [aging-session.memory :refer [aging-memory-store]]
            [aging-session.event :refer [expires-after]]
            [spothist.poller :as poller]
            [spothist.db :refer [conn]]
            [spothist.db.users :as users]
            [spothist.db.data :as data]
            [spothist.config :refer [env]]
            [spothist.spotify :as spotify]
            [spothist.util :refer [random-string]]
            [spothist.payload :as payload]
            [mount.core :as mount])
  (:import (java.io PipedOutputStream PipedInputStream)
           (org.apache.commons.compress.archivers.tar TarArchiveOutputStream
                                                      TarArchiveEntry)))

(defn register [{session :session
                 {:keys [public_key]} :transit-params}]
  (if (or (not (bytes? public_key)) (not= 32 (count public_key)))
    (bad-request "Invalid public key")
    (let [user (assoc (:user session) :public_key public_key)]
      (users/insert-user conn user)
      (poller/add-user! user)
      (-> (response {})
          (assoc :session (assoc session :user user))))))

(defn get-status [{session :session
                   id ::identity}]
  (response {:authenticated?
             (boolean id)
             :registered?
             (boolean
              (and id (seq (users/get-user conn (:user session)))))}))

(defn home-page [_]
  (redirect (route/url-for ::app-index)))

(defn app-page [_]
  (response (slurp (clojure.java.io/resource "app.html"))))

;; TODO: This is probably a bad idea
(defn stream-all
  [request]
  (let [body (PipedInputStream.)
        out (PipedOutputStream. body)]
    (a/thread
      (with-open [out out
                  taos (TarArchiveOutputStream. out)]
        (data/stream-user-payloads
         conn (get-in request [:session :user])
         (fn [{:keys [added_at payload]}]
           (doto taos
             (.putArchiveEntry
              (doto (TarArchiveEntry. added_at)
                (.setSize (count payload))))
             (.write payload)
             (.closeArchiveEntry))))))
    {:body body
     :status 200
     :headers {"Content-Type" "application/x-tar"
               "Content-Disposition" "attachment; filename=\"history.tar\""}}))

(def max-request-events (if (:dev env) 2 100))

;; TODO: request verification

(defn events-bin
  [request]
  (-> (data/get-user-payloads
       conn
       (get-in request [:session :user])
       max-request-events
       (get-in request [:query-params :start_at]))
      payload/write-records
      response))

(defn login-handler
  [{:keys [session query-params]}]
  (let [state (random-string 32)]
    (-> (redirect (spotify/get-login-redirect state))
        (assoc :session (assoc session
                               :spotify-auth-state state
                               :redirect-after-login (:page query-params))))))

(def callback-handler
  "The user is redirected here by spotify after auth"
  {:enter (fn [{{{:keys [code state]} :query-params
                 {:keys [spotify-auth-state] :as session} :session} :request :as ctx}]
            (if (or (not state) (not= state spotify-auth-state))
              (assoc ctx :response (bad-request "State mismatch!"))
              (let [session (dissoc session :spotify-auth-state)]
                (a/go
                  (if-let [res (a/<! (spotify/backend-login code))]
                    (assoc ctx :response
                           (-> (redirect (route/url-for ::app-page
                                                        :path-params
                                                        {:page (:redirect-after-login session)}))
                               (assoc :session
                                      (assoc session :user res))))
                    (assoc ctx :response
                           {:status 503
                            :body (str "Spotify auth error")
                            :headers {}
                            :session session}))))))})

(def auth-ic
  {:enter (fn [ctx]
            (if-let [identity (get-in ctx [:request :session :user :spotify_id])]
              (update ctx :request assoc ::identity identity)
              ctx))})

(def restrict-auth-ic
  {:enter (fn [ctx]
            (if (get-in ctx [:request ::identity])
              ctx
              (assoc ctx :response {:status 401 :headers {} :body "Not authorized!"})))})

(def html-ic [(body-params/body-params) http/html-body])
(def transit-ic [(body-params/body-params) http/transit-body])
(def api-ic (into [auth-ic] transit-ic))
(def restricted-ic (into [auth-ic restrict-auth-ic] transit-ic))

(defn wasm-resource [path]
  (fn [_]
    (-> (resource-response path)
        (content-type "application/wasm"))))

(def routes #{["/" :get (conj html-ic `home-page)]
              ["/app/" :get (conj html-ic auth-ic `app-page) :route-name ::app-index]
              ["/app/:page" :get (conj html-ic auth-ic `app-page) :route-name ::app-page]
              ["/login" :get (conj transit-ic `login-handler)]
              ["/callback/" :get (conj transit-ic `callback-handler)]
              ["/api/status" :get (conj api-ic `get-status)] ;; always accessible
              ["/api/stream_all" :get (conj restricted-ic `stream-all)]
              ["/api/events_bin" :get (conj restricted-ic `events-bin)]
              ["/api/register" :post (conj restricted-ic `register)]
              ;; quick hack to fix the MIME type
              ["/sql-wasm.wasm" :get (wasm-resource "sql-wasm.wasm")
               :route-name :wasm-hack]})

(defn service []
  {:env :prod
   ::http/routes routes
   ::http/join? false
   ;; We will adjust these at the nginx level
   ::http/secure-headers
   {:content-security-policy-settings {:object-src "'none'"
                                       :frame-ancestors "'none'"}}
   ::http/resource-path "/public"
   ::http/type :jetty
   ::http/enable-session {:store (aging-memory-store
                                  :refresh-on-write true
                                  :events [(expires-after 3600)])}
   ::http/port 3000
   ::http/container-options {:h2c? true
                             :h2? false
                             :ssl? false}})

(defn start []
  (-> (service)
      http/create-server
      http/start))

(defn start-dev []
  (-> (service)
      (merge {:env :dev
              ::http/join? false
              ::http/routes #(route/expand-routes (deref #'routes))
              ::http/allowed-origins {:creds true :allowed-origins (constantly true)}})
      http/default-interceptors
      http/dev-interceptors
      http/create-server
      http/start))

(mount/defstate ^{:on-reload :noop} http-server
  :start
  (if (:dev env)
    (start-dev)
    (start))
  :stop
  (http/stop http-server))
