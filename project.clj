(defproject fulcrologic/fulcro-inspect "0.1.0-SNAPSHOT"
  :description "A debugging tool for Fulcro that allows you to inspect state and other aspects of the running application(s)."
  :url "https://github.com/fulcrologic/fulcro-inspect"
  :min-lein-version "2.7.0"
  :license {:name "MIT Public License"
            :url  "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.8.0" :scope "provided"]
                 [org.clojure/clojurescript "1.9.946" :scope "provided"]
                 [clojure-future-spec "1.9.0-beta4"]
                 [fulcrologic/fulcro "1.2.0"]
                 [fulcrologic/fulcro-css "1.0.0"]

                 [org.clojure/tools.namespace "0.3.0-alpha4" :scope "test"]
                 [lein-doo "0.1.7" :scope "test"]
                 [fulcrologic/fulcro-spec "1.0.0" :scope "test" :exclusions [fulcrologic/fulcro]]]

  :source-paths ["src"]

  :figwheel {:server-port 3389}

  :jar-exclusions [#"public/.*"]

  ;; CI tests: Set up to support karma runner.
  :doo {:build "automated-tests"
        :paths {:karma "node_modules/karma/bin/karma"}}

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
                       {:id           "automated-tests"
                        :source-paths ["src" "test"]
                        :compiler     {:main          fulcro.inspect.ci-test-main
                                       :output-to     "resources/public/js/compiled/ci/test.js"
                                       :output-dir    "resources/public/js/compiled/ci/out"
                                       :asset-path    "js/compiled/ci/out"
                                       ;:preloads      [fulcro.inspect.preload]
                                       :optimizations :none}}
                       {:id           "test"
                        :source-paths ["src" "test"]
                        :figwheel     {:on-jsload cljs.user/on-load}
                        :compiler     {:main          cljs.user
                                       :output-to     "resources/public/js/compiled/test/test.js"
                                       :output-dir    "resources/public/js/compiled/test/out"
                                       :asset-path    "js/compiled/test/out"
                                       :preloads      [devtools.preload fulcro.inspect.preload]
                                       :optimizations :none}}]}

  :profiles {:dev {:plugins      [[lein-cljsbuild "1.1.7"]
                                  [lein-doo "0.1.8"]]

                   :dependencies [[devcards "0.2.3" :exclusions [cljsjs/react cljsjs/react-dom]]
                                  [figwheel-sidecar "0.5.13" :exclusions [org.clojure/tools.nrepl]]
                                  [binaryage/devtools "0.9.7"]
                                  [org.clojure/test.check "0.9.0"]]}})
