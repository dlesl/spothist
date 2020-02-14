(ns spothist.db.users
  (:require [clojure.java.jdbc :as j]))

(def users-ddl
  [[:spotify_id "varchar primary key"]
   [:added_at "timestamp not null default current_timestamp"]
   [:public_key "blob not null"]
   [:refresh_token "varchar"] ;; -- set to null when invalid
   ;; mutable fields
   [:access_token "varchar"]
   [:token_expiry "timestamp"]
   [:last_polled "timestamp"]
   [:cursor_after "integer"]]) ;; a unix time in ms (this is what spotify gives us)

(def users-keys
  (set (map first users-ddl)))

(defn- filter-keys [user]
  (select-keys user users-keys))

(defn create-users-table [db]
  (j/db-do-commands db [(j/create-table-ddl :users users-ddl {:conditional? true})]))

(defn insert-user [db user]
  (j/insert! db :users (filter-keys user)))

(defn update-user [db user]
  (j/update! db :users
             (dissoc (filter-keys user) :spotify_id)
             ["spotify_id = ?" (:spotify_id user)]))

(defn update-user-last-polled [db user]
  (j/execute! db
              ["UPDATE users SET last_polled = current_timestamp WHERE spotify_id = ?"
               (:spotify_id user)]))

(defn delete-user [db user]
  (j/delete! db :users ["spotify_id = ?" (:spotify_id user)]))

(defn get-user [db user]
  (j/query db ["SELECT * from users WHERE spotify_id = ?" (:spotify_id user)]))

(defn get-active-users [db]
  ;; resume polling in the correct order at startup
  (j/query db ["SELECT * from users WHERE refresh_token IS NOT NULL ORDER BY last_polled"]))

