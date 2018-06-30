(ns fulcro.inspect.lib.version-cards
  (:require [devcards.core :as dc :include-macros true]
            [cljs.test :refer [is testing]]
            [fulcro.inspect.lib.version :as version]))

(dc/deftest test-parse-version
  (is (= (version/parse-version "2.2.0")
         [2 2 0 1 nil 1]))

  (is (= (version/parse-version "2.2.0-beta8")
         [2 2 0 0 "beta8" 1]))

  (is (= (version/parse-version "2.2.0-beta8-SNAPSHOT")
         [2 2 0 0 "beta8" 0])))

(dc/deftest test-compare
  (is (= (version/compare "2.3.0" "2.1.0") 1))
  (is (= (version/compare "2.3.0" "2.3.0") 0))
  (is (= (version/compare "1.3.0" "2.3.0") -1)))

(dc/deftest test-sort
  (is (= (version/sort ["2.1.0-alpha1" "2.2.0" "2.3.0" "2.2.0-beta8" "2.2.1-beta8" "2.2.1-beta8-SNAPSHOT"])
         ["2.1.0-alpha1"
          "2.2.0-beta8"
          "2.2.0"
          "2.2.1-beta8-SNAPSHOT"
          "2.2.1-beta8"
          "2.3.0"])))
