(ns spothist.sqlite
  (:require
   [sql]
   [clojure.string :as str]
   [goog.object :as obj]
   [goog.array :as arr]))

(set! *warn-on-infer* true)

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

(defn create
  "remember to (.close) it later!!! no GC here. data can be nil.
  If data is specified, no tables are created."
  [data]
  (let [db (if data
             (-> data
                 (js/Uint8Array.)
                 (js/SQL.Database.))
             (doto (js/SQL.Database.)
               (.exec schema)))]
    {:db db
     :stmt-insert-track
     (.prepare db "
        REPLACE INTO track (id, name, disc_number, track_number, type, duration_ms, explicit)
        VALUES (?,?,?,?,?,?,?)")
     :stmt-insert-artist
     (.prepare db "REPLACE INTO artist (id, name, type) VALUES (?, ?, ?)")
     :stmt-insert-artist_tracks
     (.prepare db "REPLACE INTO artist_tracks (artist_id, track_id) VALUES (?, ?)")
     :stmt-insert-play
     (.prepare db "INSERT INTO play (track_id, played_at, type) VALUES (?, ?, ?)")}))

(defn close [db] (.close ^js/SQL.Database (:db db)))

(defn ^:private get-keys-arr [obj & ks]
  (arr/map (apply array ks) #(obj/get obj %)))

(defn insert-plays
  [{:keys [db stmt-insert-track stmt-insert-artist
           stmt-insert-artist_tracks stmt-insert-play]} items]
  (.run db "BEGIN TRANSACTION")
  (try
    (doseq [item items]
      (if-let [track (obj/get item "track")]
        (do (.run stmt-insert-track
                  ;; avoiding clj->js here is a ~5x speedup
                  (get-keys-arr track
                                "id"
                                "name"
                                "disc_number"
                                "track_number"
                                "type"
                                "duration_ms"
                                "explicit"))
            (doseq [artist (obj/get track "artists")]
              (.run stmt-insert-artist
                    (get-keys-arr artist "id" "name" "type"))
              (.run stmt-insert-artist_tracks
                    (array (obj/get artist "id") (obj/get track "id"))))
            (.run stmt-insert-play
                  (array (obj/get track "id") (obj/get item "played_at") (obj/get track "type"))))
        (js/console.log "not a track, skipping")))
    (catch js/Error e
      (.run db "ROLLBACK TRANSACTION")
      (throw e)))
  (.run db "COMMIT TRANSACTION"))

(defn query
  [{db :db} sql]
  (-> db
      (.exec sql)
      (js->clj :keywordize-keys true)))

(defn get-schema
  [db]
  (str/join
   ";\n"
   (-> db
       (query "SELECT sql FROM sqlite_master WHERE type='table'")
       (first)
       (:values)
       (flatten))))

(defn export
  [{db :db}]
  (.export db))
