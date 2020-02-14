(ns spothist.db
  (:require [spothist.db.users :refer [create-users-table]]
            [spothist.db.data :refer [create-data-table]]
            [spothist.config :refer [env]]
            [clojure.tools.logging :as log]
            [mount.core :as mount])
  (:import (com.mchange.v2.c3p0 ComboPooledDataSource)))

(defn- init-pool []
  (let [file (:database-file env)]
    (assert (seq file))
    (let [cpds (doto (ComboPooledDataSource.)
                 (.setJdbcUrl
                  (str "jdbc:sqlite:"
                       file
                       "?journal_mode=WAL")))
          ds {:datasource cpds}]
      (log/info "Setting up database" file)
      (create-users-table ds)
      (create-data-table ds)
      ds)))

(mount/defstate conn
  :start (init-pool)
  :stop (.close (:datasource conn)))

(defn chk1
  "Checks that a `:n` operation updated exactly one row"
  [f & args]
  (let [n (apply f args)]
    (if-not (= n 1)
      (log/error "Operation intended to update 1 row updated" n))
    n))
