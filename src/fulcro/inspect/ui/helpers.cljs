(ns fulcro.inspect.ui.helpers
  (:require [fulcro-css.css :as css]
            [clojure.string :as str]
            [goog.object :as gobj]
            [fulcro.client.primitives :as fp]))

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

(defn computed-factory [class]
  (let [factory (fp/factory class)]
    (fn [props computed]
      (factory (fp/computed props computed)))))

(defn normalize-id [id]
  (if-let [[_ prefix] (re-find #"(.+?)(-\d+)$" (str id))]
    (cond
      (keyword? id) (keyword (subs prefix 1))
      (symbol? id) (symbol prefix)
      :else prefix)
    id))

(defn ref-app-id
  "Extracts the app id from a reference."
  [ref]
  (assert (and (vector? ref)
               (vector? (second ref)))
    "Ref with app it must be in the format: [:id-key [::app-id app-id]]")
  (let [[_ [_ app-id]] ref]
    (normalize-id app-id)))

(defn comp-app-id [comp]
  (-> comp fp/get-ident ref-app-id))
