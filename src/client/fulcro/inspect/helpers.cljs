(ns fulcro.inspect.helpers
  (:require
    [cljs.pprint]
    [cognitect.transit :as transit]
    [com.cognitect.transit.types :as transit.types]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.algorithms.normalized-state :as fns]
    [com.fulcrologic.fulcro.components :as fp]
    [edn-query-language.core :as eql]))

(defn query-component
  [this]
  (fns/ui->props this))

(defn swap-entity! [{:keys [state ref]} & args]
  (apply swap! state update-in ref args))

(defn resolve-path [state path]
  (loop [[h & t] path
         new-path []]
    (if h
      (let [np (conj new-path h)
            c  (get-in state np)]
        (if (eql/ident? c)
          (recur t c)
          (recur t (conj new-path h))))
      new-path)))

(defn swap-in! [{:keys [state ref]} path & args]
  (let [path (resolve-path @state (into ref path))]
    (if (and path (get-in @state path))
      (apply swap! state update-in path args))))


(defn create-entity! [{:keys [state ref]} x data & named-parameters]
  (let [named-parameters (->> (partition 2 named-parameters)
                           (map (fn [[op path]] [op (conj ref path)]))
                           (apply concat))
        data'            (if (-> data meta ::initialized)
                           data
                           (fp/get-initial-state x data))]
    (apply swap! state merge/merge-component x data' named-parameters)
    data'))

(defn- dissoc-in [m path]
  (cond-> m
    (get-in m (butlast path))
    (update-in (butlast path) dissoc (last path))))

(defn deep-remove-ref [state ref]
  "Remove a ref and all linked refs from it."
  (let [item   (get-in state ref)
        idents (into []
                 (comp (keep (fn [v]
                               (cond
                                 (eql/ident? v)
                                 [v]

                                 (and (vector? v)
                                   (every? eql/ident? v))
                                 v)))
                   cat)
                 (vals item))]
    (reduce
      (fn [s i] (deep-remove-ref s i))
      (dissoc-in state ref)
      idents)))

(defn remove-edge! [{:keys [state ref]} field]
  (let [children (get-in @state (conj ref field))]
    (cond
      (eql/ident? children)
      (swap! state (comp #(update-in % ref dissoc field)
                     #(deep-remove-ref % children)))

      (seq children)
      (swap! state (comp #(assoc-in % (conj ref field) [])
                     #(reduce deep-remove-ref % children))))))

(defn vec-remove-index [i v]
  "Remove an item from a vector via index."
  (->> (concat (subvec v 0 i)
         (subvec v (inc i) (count v)))
    (vec)))

(defn ref-app-uuid
  "Extracts the app uuid from a ident."
  [ref]
  (assert (and (vector? ref)
            (vector? (second ref)))
    "Ref with app it must be in the format: [:id-key [::app-id app-id]]")
  (let [[_ [_ app-id]] ref]
    app-id))

(defn ref-app-id
  [state ref]
  (ref-app-uuid ref))

(defn ref-client-connection-id
  [state ref]
  (let [app-uuid (ref-app-uuid ref)]
    (get-in state [:fulcro.inspect.ui.inspector/id
                   app-uuid
                   :fulcro.inspect.core/client-connection-id])))

(defn comp-app-uuid
  "Read app uuid from a component"
  [comp]
  (-> comp fp/get-ident ref-app-uuid))

(defn remote-mutation [{:keys [state ast ref]} key]
  (-> (assoc ast :key key)
    (assoc-in [:params :fulcro.inspect.core/client-connection-id] (ref-client-connection-id @state ref))
    (assoc-in [:params :fulcro.inspect.core/app-uuid] (ref-app-uuid ref))))

(defn pr-str-with-reader [^clj x]
  (if (transit/tagged-value? x)
    (str "#" (.-tag x) " " (.-rep x))
    (try
      (try
        (pr-str x)
        (catch :default _
          (str x)))
      (catch :default _
        "UNSUPPORTED VALUE"))))

(extend-protocol IPrintWithWriter
  transit.types/TaggedValue
  (-pr-writer [x writer _]
    (write-all writer (pr-str-with-reader x))))

(defn pprint-default-handler [x]
  (-write *out* (pr-str-with-reader x)))

(-add-method cljs.pprint/simple-dispatch :default pprint-default-handler)

(defn pprint [x]
  (with-out-str (cljs.pprint/pprint x)))

(defn current-app-uuid [state-map]
  (some-> state-map
    :fulcro.inspect.ui.multi-inspector/multi-inspector
    (get "main")
    :fulcro.inspect.ui.multi-inspector/current-app
    second
    second))
