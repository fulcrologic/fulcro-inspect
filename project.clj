(defproject fulcrologic/fulcro-inspect "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0" :scope "provided"]
                 [org.clojure/clojurescript "1.9.946" :scope "provided"]
                 [clojure-future-spec "1.9.0-beta4"]
                 [fulcrologic/fulcro "1.2.0"]
                 [fulcrologic/fulcro-css "1.0.0"]
                 [fulcrologic/fulcro-spec "1.0.0" :scope "test" :exclusions [fulcrologic/fulcro]]]

  :source-paths ["src" "devcards"]

  :figwheel {:server-port 3389}

  :jar-exclusions [#"public/.*"]

  :cljsbuild {:builds [{:id           "devcards"
                        :source-paths ["src" "devcards"]
                        :figwheel     {:devcards true}
                        :compiler     {:main                 fulcro.inspect.devcards
                                       :asset-path           "js/compiled/devcards_out"
                                       :output-to            "resources/public/js/compiled/devcards.js"
                                       :output-dir           "resources/public/js/compiled/devcards_out"
                                       :preloads             [devtools.preload fulcro.inspect.preload]
                                       :external-config      {:fulcro.inspect/config {:launch-keystroke "ctrl-f"}}
                                       :parallel-build       true
                                       :source-map-timestamp true}}
                       {:id           "test"
                        :source-paths ["src" "test"]
                        :figwheel     {:on-jsload cljs.user/on-load}
                        :compiler     {:main          cljs.user
                                       :output-to     "resources/public/js/compiled/test/test.js"
                                       :output-dir    "resources/public/js/compiled/test/out"
                                       :asset-path    "js/compiled/test/out"
                                       :preloads      [devtools.preload fulcro.inspect.preload]
                                       :optimizations :none}}]}

  :profiles {:dev {:dependencies [[devcards "0.2.3"]
                                  [figwheel-sidecar "0.5.13"]
                                  [binaryage/devtools "0.9.4"]
                                  [org.clojure/test.check "0.9.0"]]}})
