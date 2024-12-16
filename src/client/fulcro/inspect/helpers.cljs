(ns fulcro.inspect.helpers
  (:require
    [cljs.pprint]
    [cognitect.transit :as transit]
    [com.cognitect.transit.types :as transit.types]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.algorithms.normalized-state :as fns]
    [com.fulcrologic.fulcro.components :as fp]
    [com.fulcrologic.fulcro.mutations :as mutations]
    [fulcro.inspect.lib.local-storage :as storage]
    [taoensso.timbre :as log]))

(defn- om-ident? [x]
  (and (vector? x)
    (= 2 (count x))
    (keyword? (first x))))

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
        (if (om-ident? c)
          (recur t c)
          (recur t (conj new-path h))))
      new-path)))

(defn get-in-path
  "Like get-in, but will resolve path before reading it."
  [state path]
  (get-in state (resolve-path state path)))

(defn swap-in! [{:keys [state ref]} path & args]
  (let [path (resolve-path @state (into ref path))]
    (if (and path (get-in @state path))
      (apply swap! state update-in path args))))

(defn integrate-ident
  "Integrate an ident into any number of places in the app state. This function is safe to use within mutation
  implementations as a general helper function.
  The named parameters can be specified any number of times. They are:
  - set: A vector (path) to a list in your app state where this new object's ident should be set.
  - append:  A vector (path) to a list in your app state where this new object's ident should be appended. Will not append
  the ident if that ident is already in the list.
  - prepend: A vector (path) to a list in your app state where this new object's ident should be prepended. Will not append
  the ident if that ident is already in the list.
  - replace: A vector (path) to a specific location in app-state where this object's ident should be placed. Can target a to-one or to-many.
   If the target is a vector element then that element must already exist in the vector."
  [state ident & named-parameters]
  {:pre [(map? state)]}
  (let [actions (partition 2 named-parameters)]
    (reduce (fn [state [command data-path]]
              (let [already-has-ident-at-path? (fn [data-path] (some #(= % ident) (get-in state data-path)))]
                (case command
                  :set (assoc-in state data-path ident)
                  :prepend (if (already-has-ident-at-path? data-path)
                             state
                             (do
                               (assert (vector? (get-in state data-path)) (str "Path " data-path " for prepend must target an app-state vector."))
                               (update-in state data-path #(into [ident] %))))
                  :append (if (already-has-ident-at-path? data-path)
                            state
                            (do
                              (assert (vector? (get-in state data-path)) (str "Path " data-path " for append must target an app-state vector."))
                              (update-in state data-path conj ident)))
                  :replace (let [path-to-vector (butlast data-path)
                                 to-many?       (and (seq path-to-vector) (vector? (get-in state path-to-vector)))
                                 index          (last data-path)
                                 vector         (get-in state path-to-vector)]
                             (assert (vector? data-path) (str "Replacement path must be a vector. You passed: " data-path))
                             (when to-many?
                               (do
                                 (assert (vector? vector) "Path for replacement must be a vector")
                                 (assert (number? index) "Path for replacement must end in a vector index")
                                 (assert (contains? vector index) (str "Target vector for replacement does not have an item at index " index))))
                             (assoc-in state data-path ident))
                  (throw (ex-info "Unknown post-op to merge-state!: " {:command command :arg data-path})))))
      state actions)))

(defn named-params-with-ref [ref named-parameters]
  (->> (partition 2 named-parameters)
    (map (fn [[op path]] [op (conj ref path)]))
    (apply concat)))

(defn create-entity! [{:keys [state ref]} x data & named-parameters]
  (let [named-parameters (->> (partition 2 named-parameters)
                           (map (fn [[op path]] [op (conj ref path)]))
                           (apply concat))
        data'            (if (-> data meta ::initialized)
                           data
                           (fp/get-initial-state x data))]
    (apply swap! state merge/merge-component x data' named-parameters)
    data'))

(defn create-entity-pm! [{:keys [state ref]} x data & named-parameters]
  (apply swap! state merge/merge-component x data
    (named-params-with-ref ref named-parameters)))

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
                                 (om-ident? v)
                                 [v]

                                 (and (vector? v)
                                   (every? om-ident? v))
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
      (om-ident? children)
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

(mutations/defmutation persistent-set-props [{::keys [local-key storage-key value]}]
  (action [env]
    (storage/set! storage-key value)
    (swap-entity! env assoc local-key value)))

(defn persistent-set! [comp local-key storage-key value]
  (fp/transact! comp [(list `persistent-set-props {::local-key   local-key
                                                   ::storage-key storage-key
                                                   ::value       value}) local-key]))

(defn normalize-id [id]
  (if-let [[_ prefix] (re-find #"(.+?)(-\d+)$" (str id))]
    (cond
      (keyword? id) (keyword (subs prefix 1))
      (symbol? id) (symbol prefix)
      :else prefix)
    id))

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

(defn all-apps [state]
  (get-in state [:fulcro.inspect.ui.multi-inspector/multi-inspector
                 "main"
                 :fulcro.inspect.ui.multi-inspector/inspectors]))

(defn matching-apps [state app-id]
  (->> (all-apps state)
    (filterv #(= app-id (:fulcro.inspect.core/app-id (get-in state %))))
    (mapv second)))

(defn update-matching-apps [state app-id f]
  (let [apps (matching-apps state app-id)]
    (reduce
      (fn [s app]
        (f s app))
      state
      apps)))

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
    second))
