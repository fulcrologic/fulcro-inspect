(ns fulcro.inspect.ui.helpers
  (:require [com.fulcrologic.fulcro-css.css :as css]
            [clojure.string :as str]
            [goog.object :as gobj]
            [com.fulcrologic.fulcro.components :as fp]))

(defn js-get-in [x path]
  (gobj/getValueByKeys x (clj->js path)))

(defn html-attr-merge [a b]
  (cond
    (map? a) (merge a b)
    (string? a) (str a " " b)
    :else b))

(defn props->html
  [attrs & props]
  (->> (mapv #(dissoc % :react-key) props)
       (apply merge-with html-attr-merge (dissoc attrs :react-key))
       (into {} (filter (fn [[k _]] (simple-keyword? k))))
       (clj->js)))

(defn expand-classes [css classes]
  {:className (str/join " " (mapv css classes))})

(defn props
  [comp defaults]
  (props->html defaults (fp/props comp)))

(defn props+classes [comp defaults]
  (let [props (fp/props comp)
        css   (-> comp fp/react-type css/get-classnames)]
    (props->html defaults
                 (expand-classes css (:fulcro.inspect.ui.core/classes props))
                 props)))

(defn computed-factory
  ([class] (computed-factory class {}))
  ([class options]
   (let [factory (fp/factory class options)]
     (fn real-factory
       ([props] (real-factory props {}))
       ([props computed]
        (factory (fp/computed props computed)))))))

(defn react-display-name [element]
  (or
    (some-> element (gobj/get "displayName") symbol)
    (some-> element fp/react-type (gobj/get "displayName") symbol)))
