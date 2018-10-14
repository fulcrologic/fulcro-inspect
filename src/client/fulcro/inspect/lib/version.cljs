(ns fulcro.inspect.lib.version
  (:refer-clojure :exclude [compare sort sort-by]))

(def last-inspect-version "2.2.4")

(defn parse-int [s]
  (js/parseInt s))

(defn vector-compare [[value1 & rest1] [value2 & rest2]]
  (let [result (cljs.core/compare value1 value2)]
    (cond
      (not (zero? result)) result
      (nil? value1) 0
      :else (recur rest1 rest2))))

(defn parse-version [s]
  (if-let [[_ major minor patch suffix snapshot] (re-find #"(\d+)\.(\d+)\.(\d+)(?:-(\w+))?(-SNAPSHOT)?$" s)]
    [(parse-int major) (parse-int minor) (parse-int patch) (if suffix 0 1) suffix (if snapshot 0 1)]))

(defn compare [a b]
  (vector-compare
    (parse-version a)
    (parse-version b)))

(defn sort [coll] (clojure.core/sort compare coll))

(defn sort-by [keyfn coll]
  (clojure.core/sort-by keyfn compare coll))
