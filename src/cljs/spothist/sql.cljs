(ns spothist.sql
  "A thin wrapper around SQL.js (SQLite)"
  (:require-macros [cljs.core.async.macros :refer [go-loop]])
  (:require  [cljs.core.async :refer [<! put! close! chan]]
             [clojure.string :as str]
             [goog.object :as obj]
             [goog.array :as arr]
             [promesa.core :as p]))

(set! *warn-on-infer* true)

;; worker setup

(defn sql-worker []
  (let [worker (js/Worker. "/worker.js")
        msgs (chan 100)
        responses (chan)]
    (doto worker
      ;; if worker.onerror fires then it's broken and we can no longer
      ;; use it. Invalid queries etc. return errors using postMessage.
      (set! -onerror (fn [error]
                       (put! responses {:error (js->clj error)})
                       (close! msgs)))
      (set! -onmessage #(put! responses (-> %
                                            (obj/get "data")
                                            (js->clj :keywordize-keys true)))))
    (go-loop []
      (when-let [[msg promise] (<! msgs)]
        (.postMessage worker (clj->js msg))
        (let [{:keys [error result]} (<! responses)]
          (if error
            (p/reject! promise (ex-info "error" error))
            (p/resolve! promise result)))
        (recur)))
    {:worker worker
     :msgs msgs}))

(defn request [worker msg]
  (let [p (p/deferred)]
    (put! (:msgs worker) [msg p])
    p))

(defn close [{:keys [worker]}]
  (.terminate ^js/Worker worker))

(declare schema)

(defn open
  "Data can be nil, or an ArrayBuffer.
  If data is specified, no tables are created."
  [worker data]
  (println "data" data)
  (if data
    (request worker {:action "open" :buffer data})
    (-> (request worker {:action "open"})
        (p/then #(request worker {:action "exec"
                                  :sql schema})))))

(defn ^:private get-keys-arr [obj & ks]
  (arr/map (apply array ks) #(obj/get obj %)))

(defn artist-stmts [artist track_id]
  [["REPLACE INTO artist (id, name, type) VALUES (?, ?, ?)"
    (get-keys-arr artist "id" "name" "type")]
   ["REPLACE INTO artist_tracks (artist_id, track_id) VALUES (?, ?)"
    [(obj/get artist "id") track_id]]])

(defn track-stmts [item]
  (if-let [track (obj/get item "track")]
    (concat
     [["REPLACE INTO track (id, name, disc_number, track_number, type, duration_ms, explicit)
        VALUES (?,?,?,?,?,?,?)"
       (get-keys-arr track "id" "name" "disc_number" "track_number" "type" "duration_ms" "explicit")]
      ["INSERT INTO play (track_id, played_at, type) VALUES (?, ?, ?)"
       (array (obj/get track "id") (obj/get item "played_at") (obj/get track "type"))]]
     (mapcat #(artist-stmts % (obj/get track "id")) (obj/get track "artists")))))

(defn insert-plays
  [worker items]
  (request worker {:action "transaction"
                   :statements (mapcat track-stmts items)}))

(defn query [worker sql]
  (request worker {:action "exec" :sql sql}))

(defn get-schema
  [worker]
  (-> (query worker "SELECT sql FROM sqlite_master WHERE type='table'")
      (p/then #(str/join ";\n" (-> %
                                   (first)
                                   (:values)
                                   (flatten))))))

(defn export
  [worker]
  (request worker {:action "export"}))

(def schema
  "CREATE TABLE track (
    id VARCHAR PRIMARY KEY,
    name VARCHAR,
    disc_number INTEGER,
    track_number INTEGER,
    type VARCHAR,
    duration_ms INTEGER,
    explicit BOOLEAN
);
CREATE TABLE artist (
    id VARCHAR PRIMARY KEY,
    name VARCHAR,
    type VARCHAR
);
CREATE TABLE artist_tracks (
    artist_id VARCHAR,
    track_id VARCHAR,
    FOREIGN KEY (artist_id)
        REFERENCES artist(id),
    FOREIGN KEY (track_id)
        REFERENCES track(id),
    PRIMARY KEY (artist_id, track_id)
);
CREATE TABLE play (
    track_id VARCHAR,
    played_at VARCHAR,
    type VARCHAR,
    FOREIGN KEY (track_id)
        REFERENCES track(id)
);")

(def default-query
  "SELECT * FROM play LIMIT 100;
SELECT * FROM track LIMIT 100;
SELECT * FROM artist LIMIT 100")

(def examples
  [{:name
    "Recently played"
    :sql
    "SELECT played_at, GROUP_CONCAT(a.name, ', ') artists, t.name FROM
    (SELECT track_id, played_at, rowid FROM play
        ORDER BY played_at DESC LIMIT 10) AS p
    LEFT JOIN track t ON p.track_id = t.id
    LEFT JOIN artist_tracks at ON at.track_id = t.id
    LEFT JOIN artist a ON artist_id = a.id
    GROUP BY p.rowid
    ORDER BY played_at DESC"}
   {:name
    "Most played"
    :sql
    "SELECT plays, GROUP_CONCAT(a.name, ', ') artists, t.name FROM
    (SELECT COUNT(*) plays, track_id FROM play
        GROUP BY track_id ORDER BY plays DESC LIMIT 10) AS p
    LEFT JOIN track t ON p.track_id = t.id
    LEFT JOIN artist_tracks at ON at.track_id = t.id
    LEFT JOIN artist a ON artist_id = a.id
    GROUP BY t.id
    ORDER BY plays DESC"}])
