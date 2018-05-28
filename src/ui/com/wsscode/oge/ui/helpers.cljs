(ns com.wsscode.oge.ui.helpers
  (:require [clojure.string :as str]
            [goog.object :as gobj]
            [cljs.spec.alpha :as s]))

(defn js-get-in [x path]
  (gobj/getValueByKeys x (clj->js path)))

(s/fdef js-get-in
  :args (s/cat :x any? :path vector?)
  :ret any?)

(defn html-attr-merge [a b]
  (cond
    (map? a) (merge a b)
    (string? a) (str a " " b)
    :else b))

(s/fdef html-attr-merge
  :args (s/cat :a any? :b any?)
  :ret any?)

(defn props->html
  [attrs & props]
  (->> (mapv #(dissoc % :react-key) props)
       (apply merge-with html-attr-merge (dissoc attrs :react-key))
       (into {} (filter (fn [[k _]] (simple-keyword? k))))
       (clj->js)))

(defn expand-classes [css classes]
  {:className (str/join " " (mapv css classes))})

(s/fdef expand-classes
  :args (s/cat :css map? :classes map?)
  :ret map?)

(defn strings [strings]
  (->> strings
       (map #(str "\"" % "\""))
       (str/join " ")))
