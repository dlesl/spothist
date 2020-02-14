(ns spothist.poller
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [clojure.java.jdbc :as j]
            [spothist.db :refer [conn]]
            [spothist.spotify :as spotify]
            [spothist.util :refer [fix-time success?]]
            [spothist.db.users :as users]
            [spothist.db.data :as data]
            [spothist.encode :refer [encode]]
            [mount.core :as mount])
  (:import java.time.Instant))

;; (def poll-interval 1000)
(def poll-interval (* 25 60 1000))

(def max-failures 5)

(defonce users (atom {}))

;; This is synchronous!
(defn- update-user!! [user items]
  (println "got the items, " (count items))
  (let [payload (when (seq items)
                  (encode
                   (:public_key user)
                   (reverse items)))] ;;  spotify uses reverse chronological order
    (j/with-db-transaction [t conn]
      (users/update-user t user)
      (users/update-user-last-polled t user)
      (when payload
        (data/insert-payload t user payload)))))

(defn- poll-loop
  [stop-chan user]
  (a/go-loop [user user failures 0]
    (log/info "polling user " user)
    (let [{:keys [token_expiry refresh_token]} user
          token-valid? (pos? (.compareTo (fix-time token_expiry) (Instant/now)))
          new-tokens (if-not token-valid? (a/<! (spotify/refresh-token refresh_token)))
          user (merge user new-tokens)  ; no-op if token-valid?
          {:keys [access_token cursor_after]} user
          resp (and (or token-valid? new-tokens)
                    (a/<! (spotify/get-recently-played access_token cursor_after)))]
      (if (and resp (success? resp))
        (let [items (get-in resp [:body :items])
              after (get-in resp [:body :cursors :after])
              user (assoc user :cursor_after (or after cursor_after))]
          (a/<! (a/thread (update-user!! user items)))
          (a/alt!
            stop-chan nil
            (a/timeout poll-interval) (recur user 0)))
        (if (< failures max-failures)
          (a/alt!
            stop-chan nil
            (a/timeout poll-interval) (recur user (inc failures)))
          ;; TODO: we should update the db to reflect that this user is bad
          (log/warn "dropping user" user "after" failures "failures"))))))

(defn add-user! [user]
  (let [stop (a/chan)]
    (poll-loop stop user)
    (swap! users assoc (:spotify_id user) stop)))

(defn remove-user! [user]
  (let [{:keys [spotify_id]} user]
    (if-let [c (@users spotify_id)]
      (do (a/close! c) (swap! users dissoc spotify_id))
      (log/warn "WARNING: attempted to remove nonexistant user" spotify_id))))

(mount/defstate poller                  ; maybe the atom should be
                                        ; inside this?
  :start
  (let [rows (users/get-active-users conn)]
    (when (seq rows)
      (let [delay (/ poll-interval (count rows))]
        (a/go (doseq [row rows]
                (add-user! row)
                (a/<! (a/timeout delay)))))))
  :stop
  (do (doseq [stop-chan (vals @users)]
        (a/close! stop-chan))
      (reset! users {})))
