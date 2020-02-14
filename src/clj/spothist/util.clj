(ns spothist.util
  (:require [clojure.core.async :as a]
            [hato.client :as client]
            [hato.middleware :refer [unexceptional-status?]]
            [clojure.tools.logging :as log])
  (:import java.util.Base64
           java.time.Instant
           java.security.SecureRandom))
(set! *warn-on-reflection* true)

(def alphanum "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")

(defn random-string
  "Returns a random string"
  [len]
  (let [sr (SecureRandom.)]
    (apply str (repeatedly len #(nth alphanum (.nextInt sr (count alphanum)))))))

;; One connection pool for the whole app - I think this is OK
(defonce cached-client
  (client/build-http-client {:connect-timeout 10000
                             :redirect-policy :always}))

(defn http-async
  [opts]
  (let [c (a/chan)]
    (log/info "outgoing request " (str opts))
    (client/request (assoc opts
                           :http-client cached-client
                           :async? true
                           :throw-exceptions false)
                    (partial a/put! c)
                    (fn [exception]
                      (log/error "HTTP request failed: " exception)
                      (a/close! c)))
    c))

(defn success? [response]
  (unexceptional-status? (:status response)))

(defn unbase64 [^String s]
  (-> (Base64/getDecoder) (.decode s)))

;; TODO: fix this in the database adapter
(defn fix-time [maybe-instant]
  (if (string? maybe-instant)
    (Instant/parse ^String maybe-instant)
    maybe-instant))
