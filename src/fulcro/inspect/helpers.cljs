(ns fulcro.inspect.helpers
  (:require [fulcro.client.primitives :as fp]
            [fulcro.client.mutations :as mutations]
            [fulcro.inspect.lib.local-storage :as storage]))

(defn- om-ident? [x]
  (and (vector? x)
       (= 2 (count x))
       (keyword? (first x))))

(defn query-component
  ([this]
   (let [component (fp/react-type this)
         ref       (fp/get-ident this)
         state     (-> this fp/get-reconciler fp/app-state deref)
         query     (fp/get-query component)]
     (fp/db->tree query (get-in state ref) state)))
  ([this focus-path]
   (let [component (fp/react-type this)
         ref       (fp/get-ident this)
         state     (-> this fp/get-reconciler fp/app-state deref)
         query     (fp/focus-query (fp/get-query component) focus-path)]
     (-> (fp/db->tree query (get-in state ref) state)
         (get-in focus-path)))))

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

(defn merge-entity [state x data & named-parameters]
  "Starting from a denormalized entity map, normalizes using class x.
   It assumes the entity is going to be normalized too, then get all
   normalized data and merge back into the app state and idents."
  (let [idents     (-> (fp/tree->db
                         (reify
                           fp/IQuery
                           (query [_] [{::root (fp/get-query x)}]))
                         {::root data} true)
                       (dissoc ::root ::fp/tables))
        root-ident (fp/ident x data)
        state      (merge-with (partial merge-with merge) state idents)]
    (if (seq named-parameters)
      (apply integrate-ident state root-ident named-parameters)
      state)))

(defn create-entity! [{:keys [state ref]} x data & named-parameters]
  (let [named-parameters (->> (partition 2 named-parameters)
                              (map (fn [[op path]] [op (conj ref path)]))
                              (apply concat))
        data'            (if (-> data meta ::initialized)
                           data
                           (fp/get-initial-state x data))]
    (apply swap! state merge-entity x data' named-parameters)))

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
  (fp/transact! comp [(list `persistent-set-props {::local-key local-key
                                                   ::storage-key storage-key
                                                   ::value value}) local-key]))
