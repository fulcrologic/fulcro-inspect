(ns fulcro.inspect.ui.helpers
  (:require [fulcro-css.css :as css]
            [clojure.string :as str]
            [goog.object :as gobj]
            [fulcro.client.primitives :as fp]
            [fulcro.client.impl.protocols :as p]
            [fulcro.util :as util]))

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

;;;; container factory

(def ^:dynamic *instrument* (deref #'fp/*instrument*))
(def ^:dynamic *reconciler* (deref #'fp/*reconciler*))
(def ^:dynamic *parent* (deref #'fp/*parent*))
(def ^:dynamic *shared* (deref #'fp/*shared*))
(def ^:dynamic *depth* (deref #'fp/*depth*))

(def compute-react-key (deref #'fp/compute-react-key))
(def om-props (deref #'fp/om-props))

(defn container-factory
  "Create a factory constructor from a component class created with
   fulcro.client.primitives/defui. Different from the default Om.next one, this will expand
   the children, this enables the children to be present without requiring
   each of then to specify a react key to avoid react key warnings."
  ([class] (container-factory class nil))
  ([class {:keys [validator keyfn instrument?]
           :or   {instrument? true} :as opts}]
   {:pre [(fn? class)]}
   (fn self [props & children]
     (when-not (nil? validator)
       (assert (validator props)))
     (if (and *instrument* instrument?)
       (*instrument*
         {:props    props
          :children children
          :class    class
          :factory  (container-factory class (assoc opts :instrument? false))})
       (let [key (if-not (nil? keyfn)
                   (keyfn props)
                   (compute-react-key class props))
             ref (:ref props)
             ref (cond-> ref (keyword? ref) str)
             t   (if-not (nil? *reconciler*)
                   (p/basis-t *reconciler*)
                   0)]
         (apply js/React.createElement class
           #js {:key               key
                :ref               ref
                :omcljs$reactKey   key
                :omcljs$value      (om-props props t)
                :omcljs$path       (-> props meta :om-path)
                :omcljs$reconciler *reconciler*
                :omcljs$parent     *parent*
                :omcljs$shared     *shared*
                :omcljs$instrument *instrument*
                :omcljs$depth      *depth*}
           (util/force-children children)))))))
