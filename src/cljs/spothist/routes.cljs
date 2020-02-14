(ns spothist.routes
  (:require
   [reitit.frontend :as reitit-frontend]))

(def router
  (reitit-frontend/router
   ["/app/"
    [""
     {:name ::index
      :title "Overview"
      :category "About"}]
    ["registration"
     {:name ::registration
      :title "Registration"
      :auth? true                       ; must be logged in (spotify oauth)
      :category "Your account"}]
    ["download"
     {:name ::download
      :title "Download data"
      :auth? true
      :reg? true                        ; must be in user database
      :category "Your account"}]
    ;; ["top"
    ;;  {:name ::top
    ;;   :title "Top lists"
    ;;   :auth? true
    ;;   :reg? true
    ;;   :data? true                      ; complete playback history
    ;;                                    ; should be loaded
    ;;   :category "Analysis"}]
    ;; ["economics"
    ;;  {:name ::economics
    ;;   :title "Economics"
    ;;   :auth? true
    ;;   :reg? true
    ;;   :data? true
    ;;   :category "Analysis"}]
    ["sql"
     {:name ::sql
      :title "SQL"
      :auth? true
      :reg? true
      :data? true
      :category "Analysis"}]
    ["source"
     {:name ::source
      :title "Source code"
      :external "https://github.com/dlesl/spothist/"
      :category "More info"}]]))
