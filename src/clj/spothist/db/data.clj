(ns spothist.db.data
  (:require [clojure.java.jdbc :as j]))

(def data-ddl
  [[:spotify_id "varchar"]
   [:added_at "timestamp not null default current_timestamp"]
   [:payload "blob not null"]])

(defn create-data-table [db]
  (j/db-do-commands db [(j/create-table-ddl :data data-ddl {:conditional? true})]))

(defn insert-payload [db user payload]
  (j/insert! db :data
             {:spotify_id (:spotify_id user)
              :payload payload}))

(defn stream-user-payloads
  "calls f with each row, for streaming tar file to client"
  [db user f]
  (j/query db
           ["SELECT payload, added_at
             FROM data
             WHERE spotify_id = ?
             ORDER BY added_at"
            (:spotify_id user)] {:raw? true
                                 :row-fn f}))
(defn get-user-payloads
  "start-at may be nil"
  [db user limit start-at]
  (j/query db ["SELECT payload, added_at
                FROM data
                WHERE spotify_id = ?
                AND added_at > ?
                ORDER BY added_at
                LIMIT ?"
               (:spotify_id user)
               (or start-at "")
               limit]))
