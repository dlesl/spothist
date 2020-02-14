(defproject spothist "0.1.0-SNAPSHOT"

  :description "FIXME: write description"
  :url "http://example.com/FIXME"

  :dependencies [[ch.qos.logback/logback-classic "1.2.3"]
                 [cheshire "5.10.0"]
                 [clojure.java-time "0.3.2"]
                 [com.cognitect/transit-clj "0.8.319"]
                 [cprop "0.1.15"]
                 [expound "0.8.4"]
                 [funcool/octet "1.1.2"]
                 [hato/hato "0.4.1"]
                 [kirasystems/aging-session "0.3.5"]
                 [io.pedestal/pedestal.service "0.5.7"]
                 [io.pedestal/pedestal.jetty "0.5.7"]
                 [io.pedestal/pedestal.route "0.5.7"]
                 [com.mchange/c3p0 "0.9.5.5"]
                 [org.clojure/java.jdbc "0.7.11"]
                 [org.xerial/sqlite-jdbc "3.30.1"]
                 [org.apache.commons/commons-compress "1.20"]
                 [caesium "0.12.0"]
                 [mount "0.1.16"]
                 [nrepl "0.6.0"]
                 [org.clojure/clojure "1.10.1"]
                 [org.clojure/tools.cli "0.4.2"]
                 [org.clojure/tools.logging "0.6.0"]
                 [ring/ring-core "1.8.0"]]
  
  :min-lein-version "2.0.0"
  :source-paths ["src/clj" "src/cljs" "src/cljc"]
  :resource-paths ["resources" "target/cljsbuild"]
  :target-path "target/%s/"
  :main ^:skip-aot spothist.core
  :plugins [[lein-cljsbuild "1.1.7"]]
  :clean-targets ^{:protect false}
  [:target-path [:cljsbuild :builds :app :compiler :output-dir] [:cljsbuild :builds :app :compiler :output-to]]
  :figwheel
  {:http-server-root "public"
   :server-logfile "log/figwheel-logfile.log"
   :nrepl-port 7002
   :css-dirs ["resources/public/css"]
   :nrepl-middleware [cider.piggieback/wrap-cljs-repl]}

  :profiles
  {:provided {:dependencies             ; frontend deps - only needed
                                        ; at compile time
              [[org.clojure/clojurescript "1.10.597"]
               [re-frame "0.11.0"]
               [reagent "0.9.1" :exclusions [cljsjs/react cljsjs/react-dom]]
               [cljs-ajax "0.8.0"]
               [day8.re-frame/http-fx "v0.2.0"]
               [metosin/reitit "0.4.2"]]}
   :uberjar {:omit-source true
             :prep-tasks ["compile" ["cljsbuild" "once" "min"]]
             :cljsbuild{:builds
                        {:min
                         {:source-paths ["src/cljc" "src/cljs" "env/prod/cljs"]
                          :compiler
                          {:output-dir "target/cljsbuild/public/js"
                           :output-to "target/cljsbuild/public/js/app.js"
                           :source-map "target/cljsbuild/public/js/app.js.map"
                           :optimizations :advanced
                           :pretty-print false
                           :infer-externs true
                           :npm-deps false
                           :closure-warnings
                           { ;; :externs-validation :off
                            :non-standard-jsdoc :off}
                           :externs ["react/externs/react.js"
                                     "src/js/externs.js"]
                           :foreign-libs [{:file "dist/index_bundle.js"
                                           :provides ["react" "react-dom" "ace-editor"
                                                      "sodium" "sql" "pako"]
                                           :global-exports {react React
                                                            react-dom ReactDOM
                                                            ace-editor AceEditor
                                                            sodium sodium
                                                            pako pako
                                                            sql SQL}}]}}}}

             :aot :all
             :uberjar-name "spothist.jar"
             :source-paths ["env/prod/clj"]
             :resource-paths ["env/prod/resources"]}

   :dev           [:project/dev :profiles/dev]

   :project/dev  {:jvm-opts ["-Dconf=dev-config.edn"]
                  :dependencies [[binaryage/devtools "1.0.0"]
                                 [cider/piggieback "0.4.2"]
                                 [figwheel-sidecar "0.5.19"]
                                 [prone "2020-01-17"]
                                 [re-frisk "0.5.4.1"]]
                  :plugins      [[lein-figwheel "0.5.19"]]
                  :cljsbuild{:builds
                             {:app
                              {:source-paths ["src/cljs" "src/cljc" "env/dev/cljs"]
                               :figwheel {:on-jsload "spothist.core/mount-components"}
                               :compiler
                               {:output-dir "target/cljsbuild/public/js/out"
                                :closure-defines {"re_frame.trace.trace_enabled_QMARK_" true}
                                :optimizations :none
                                :preloads [re-frisk.preload]
                                :output-to "target/cljsbuild/public/js/app.js"
                                :asset-path "/js/out"
                                :source-map true
                                :source-map-path "js"
                                :npm-deps false
                                :main "spothist.app"
                                :pretty-print true
                                :foreign-libs [{:file "dist/index_bundle.js"
                                                :provides ["react" "react-dom" "ace-editor"
                                                           "sodium" "sql" "pako"]
                                                :global-exports {react React
                                                                 react-dom ReactDOM
                                                                 ace-editor AceEditor
                                                                 sodium sodium
                                                                 pako pako
                                                                 sql SQL}}]}}}}
                  :source-paths ["env/dev/clj"]
                  :resource-paths ["env/dev/resources"]
                  :repl-options {:init-ns user}}
   :profiles/dev {}})
