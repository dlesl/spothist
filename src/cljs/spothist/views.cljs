(ns spothist.views
  (:require
   [reagent.core :as r]
   [re-frame.core :refer [subscribe dispatch]]
   [reitit.core :as reitit]
   [spothist.subs :as subs]
   [spothist.libs :refer [sql-ready sodium-ready]]
   [spothist.sqlite :as sqlite]
   [spothist.crypto :refer [encode-keypair parse-keypair generate-keypair]]
   [spothist.events :as events]
   [spothist.routes :as routes]
   [spothist.util :refer [enumerate static-html] :as util]
   [clojure.string :as str]
   [goog.object :as obj]
   [ace-editor])
  (:require-macros [spothist.macros :refer [static-slurp]]))

(def max-display-rows 1000)

(defn index []
  [:div.content.box
   [static-html (static-slurp "text/overview.html")]])

(defn keypair-input
  "A keypair input control. Calls on-change with the result of
  crypto/parse-keypair when the text is changed (nil when invalid)."
  [{:keys [on-change initial enable-generate? read-only]}]
  (let [key-edited (r/atom (encode-keypair initial))]
    (fn [_]                             ;; we ignore prop updates
      (let [valid? (parse-keypair @key-edited)
            control (atom nil)
            status-class (when (seq @key-edited)
                           (if valid? "is-success" "is-danger"))]
        [:<>
         [:label.label "Keypair"]
         [:div.field.is-grouped
          [:div.control.is-expanded
           [:input.input
            {:ref (partial reset! control)
             :type "text"
             :readOnly read-only
             :class status-class
             :value @key-edited
             :on-change #(let [val (-> % .-target .-value)]
                           (reset! key-edited val)
                           (on-change (parse-keypair val)))}]
           (when (not read-only)
             [:p.help {:class status-class}
              (cond
                (empty? @key-edited) "Please enter a keypair"
                valid? "This keypair is valid"
                :else "This keypair is invalid")])]
          (when enable-generate?
            [:p.control
             [:button.button.is-link
              {:on-click
               #(let [keypair (generate-keypair)]
                  (reset! key-edited (encode-keypair keypair))
                  (on-change keypair))}
              "Generate new"]])
          (when (or enable-generate? read-only)
            [:p.control
             [:button.button.is-info
              {:on-click
               #(do (doto @control
                      (.select)
                      (.setSelectionRange 0 99999))
                    (js/document.execCommand "copy"))}
              "Copy!"]])]]))))

(defn enter-keypair
  "Allows the user to enter an existing keypair."
  []
  (let [keypair @(subscribe [::subs/keypair])]
    [keypair-input {:initial keypair
                    :on-change #(dispatch [::events/set-keypair %])}]))

(defn requires-auth
  "This component offers to allow the user to login, if they haven't"
  [child]
  (let [status @(subscribe [::subs/status])
        route @(subscribe [::subs/current-route])]
    (if status
      (if (:authenticated? status)
        child
        [:div.content.box
         [:p "You need to login with your Spotify account to continue."]
         [:a.button.is-primary {:href (str "/login?page=" (-> route :data :name name))}
          "Log in with Spotify"]])
      [:p "loading..."])))

(defn requires-reg
  "This component offers to allow the logged in user to register, if they haven't"
  [child]
  [requires-auth
   (let [status @(subscribe [::subs/status])]
     (if status
       (if (:registered? status)
         child
         [:div.content.box
          [:p "You are logged in."]
          [:p "To continue, you must register your Spotify account and set up your keypair."]
          [:a.button.is-primary
           {:href (:path (reitit/match-by-name routes/router ::routes/registration))}
           "Register"]])
       [:p "loading..."]))])

(defn requires-libs
  "This component waits for async loaded libraries before displaying its child"
  [libs child]
  (if (every? deref libs)
    child
    [:p "loading..."]))

(defn requires-data
  "This component ensures the data has been loaded before displaying its child"
  [child]
  [requires-libs [sodium-ready sql-ready]
   [:div
    (let [events-loaded? @(subscribe [::subs/events-loaded?])]
      (if events-loaded?
        child
        [:<>
         [requires-reg
          (let [keypair @(subscribe [::subs/keypair])
                loading-events @(subscribe [::subs/loading-events])]
            [:div.content.box
             (if loading-events
               [:<>
                [:progress.progress.is-primary]
                (if-let [loading-events @(subscribe [::subs/loading-events])]
                  [:p (str loading-events " song plays loaded")]
                  [:p "Requesting events from server..."])]
               [:<>
                [:p "You need to enter your keypair to continue."]
                [enter-keypair]
                [:button.button.is-primary
                 {:disabled (not keypair)
                  :on-click #(dispatch [::events/get-events])}
                 "Submit"]])])]
         [:div.content.box
          [:p "If you just want to try it out, you can also load some (fake) demo data."]
          [:a.button {:on-click #(dispatch [::events/load-demo-data])}
           "Load demo data"]]]))]])

(defn registration []
  (let [authenticated? @(subscribe [::subs/authenticated?])
        registered? @(subscribe [::subs/registered?])
        keypair @(subscribe [::subs/keypair])]
    [requires-libs [sodium-ready]
     [:div.content.box
      [:h2 "Registration"]
      (if authenticated?
        (if registered?
          [:<>
           [:p "You are registered. Your Spotify playback history is being saved!"]
           [:p "To decrypt your data in the future, you will need the keypair you "
            "generated when you registered."]
           (if keypair
             [:div
              [:p "Here it is again, copy it somewhere safe, or "
               [:a {:href (str "mailto:?subject=my spothist keys&body="
                               (js/encodeURIComponent (encode-keypair keypair)))}
                "email it to yourself! "]
               "Only the public key has been "
               "sent to the server, it exists only in your browser and will be gone when you "
               "leave this page!"]
              [keypair-input {:read-only true
                              :initial keypair}]]
             [:p "I hope you still have it, because I don't! (At least not the secret part)."])]
          [:<>
           [:p "You are not registered, would you like to?"]
           [:p "First you must generate a keypair (or enter an existing one)."]
           [keypair-input {:enable-generate? true
                           :on-change #(dispatch [::events/set-keypair %])}]
           [:p "Keep this somewhere safe! If you lose it you will no longer be able to
access your data."]
           [:p "Once you've copied the keypair somewhere safe, click register!"]
           [:button.button.is-primary.is-fullwidth
            {:disabled (not keypair)
             :on-click #(dispatch [::events/register])}
            "Register"]])
        [:p "loading..."])]]))

(defn download []
  [:div.content.box
   [:h2 "Download your data"]
   [:p "You can download all of your data as a tar archive. "
    "The archive contains the unmodified responses received from Spotify, "
    "compressed with gzip and encrypted in "
    [:a {:href "https://libsodium.gitbook.io/doc/public-key_cryptography/sealed_boxes"
         :target "_blank"}
     "libsodium sealed boxes"]
    ". You will need the keypair you created when you registered to decrypt it. "
    "A python script capable of decrypting the tar file can be "
    [:a {:href "/decode.py"
         :target "_blank"} "found here"] "."]
   [:form
    [:button.button.is-primary.is-fullwidth
     {:type "submit"
      :formAction "/api/stream_all"} "Download archive"]]])

(defn schema-panel []
  (let [schema @(subscribe [::subs/schema])]
    [:div.panel
     {:style {:width "20em"}}
     [:p.panel-heading "Schema"]
     [:div.panel-block
      (let [lines (count (str/split-lines schema))]
        [:> ace-editor
         {:ref #(try ;; HACK ;)
                  (-> %
                      (obj/getValueByKeys
                       (array "editor" "renderer" "$cursorLayer" "element" "style"))
                      (obj/set "opacity" 0))
                  (catch js/Error _ nil))
          :mode "sql"
          :theme "textmate"
          :showGutter false
          :showPrintMargin false
          :highlightActiveLine false
          :minLines lines
          :maxLines lines
          :readOnly true
          :name "schema"
          :value schema}])]]))

(defn data-view []
  (let [selected-tab (r/atom 0)
        data (subscribe [::subs/query-result])]
    (fn [_]
      [:nav.panel
       [:p.panel-heading "Results"]
       (when (> (count @data) 1)
         [:p.panel-tabs
          (doall
           (for [[i _] (enumerate @data)]
             ^{:key i} [:a {:class (when (= @selected-tab i) "is-active")
                            :on-click #(reset! selected-tab i)} (str "Result " (inc i))]))])
       (if-not (empty? @data)
         (let [{:keys [columns values]} (nth @data (min @selected-tab (dec (count @data))))]
           [:div.table-container
            [:table.table.is-fullwidth
             [:thead
              [:tr
               (for [[i col] (enumerate columns)]
                 ^{:key i} [:th col])]]
             [:tbody
              (if (<= (count values) max-display-rows)
                (for [[i row] (map-indexed vector values)]
                  ^{:key i} [:tr
                             (for [[j val] (map-indexed vector row)]
                               ^{:key j} [:td val])])
                [:tr [:td {:colSpan (count columns)}
                      "Not displaying " (count values) " values. "
                      "Please add a LIMIT clause (max " max-display-rows ")."]])]]]))])))

(defn sql []
  (let [query (r/atom sqlite/default-query)
        events-loaded? (subscribe [::subs/events-loaded?])]
    (fn [_]
      [:div.columns
       [:div.column
        [:nav.panel
         [:p.panel-heading "SQL Editor"]
         [:div.panel-block
          [:> ace-editor
           {:mode "sql"
            :theme "textmate"
            :showGutter false
            :showPrintMargin false
            :highlightActiveLine false
            :width "100%"
            :height "10em"
            :name "sql-editor"
            :value @query
            :onChange #(reset! query %)}]]
         [:div.panel-block
          [:button.button.is-fullwidth.is-outlined
           {:disabled (not @events-loaded?)
            :on-click #(dispatch [::events/eval-sql @query])}
           "execute"]]]
        [data-view]]
       [:div.column.is-narrow
        [:div.panel
         [:p.panel-heading "Tools"]
         [:a.panel-block
          {:on-click #(when (js/confirm "All DB changes and your input SQL will be lost, continue?")
                        (dispatch [::events/reset-sql]))}
          "Reset"]
         [:a.panel-block
          {:on-click #(dispatch [::events/export-sql nil])}
          "Export"]]
        [:div.panel
         [:p.panel-heading "Examples"]
         (for [{:keys [name sql]} sqlite/examples]
           ^{:key name} [:a.panel-block
                         {:on-click #(do
                                       (reset! query sql)
                                       (dispatch [::events/eval-sql sql]))}
                         name])]
        [schema-panel]]])))

(defn menu []
  (let [current-route @(subscribe [::subs/current-route])]
    [:aside.menu
     (let [cats (->> (reitit/route-names routes/router)
                     (map (partial reitit/match-by-name routes/router))
                     (group-by (comp :category :data)))]
       (for [[cat routes] (seq cats)]
         ^{:key cat}
         [:<>
          [:p.menu-label cat]
          [:ul.menu-list
           (for [route routes]
             (let [{:keys [path data]} route
                   {:keys [name title external]} data]
               ^{:key name}
               [:li
                [:a
                 {:class
                  (when (= name (get-in current-route [:data :name]))
                    "is-active")
                  :href (or external path)
                  :target (when external "_blank")}
                 title]]))]]))]))

(def route-map
  {::routes/index index
   ::routes/registration (fn [] [requires-auth registration])
   ::routes/download (fn [] [requires-reg download])
   ::routes/sql (fn [] [requires-data [sql]])})

(defn main-panel []
  (let [current-route @(subscribe [::subs/current-route])]
    [:div
     [:nav.navbar.is-light.is-spaced
      [:div.navbar-brand [:div.navbar-item [:h1.title "Spotify History Logger"]]]]
     [:section.section
      [:div.columns.is-6.is-variable
       [:div.column.is-narrow
        [menu]]
       [:div.column
        [(or (-> current-route :data :name route-map) index)]]]]]))
