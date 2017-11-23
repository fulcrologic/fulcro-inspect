(ns fulcro.inspect.helpers
  (:require [fulcro.client.core :as fulcro]
            [om.next :as om]))

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
  (let [{::keys [root] :as idents}
        (om/tree->db
          (reify
            om/IQuery
            (query [_] [{::root (om/get-query x)}]))
          {::root data} true)
        root-ident (om/ident x data)
        idents     (dissoc idents ::root ::om/tables)
        state      (merge-with (partial merge-with merge) state idents)]
    (if (seq named-parameters)
      (apply integrate-ident state root-ident named-parameters)
      state)))

(defn create-entity! [{:keys [state ref]} x data & named-parameters]
  (let [named-parameters (->> (partition 2 named-parameters)
                              (map (fn [[op path]] [op (conj ref path)]))
                              (apply concat))
        data' (if (-> data meta ::initialized)
                data
                (fulcro/get-initial-state x data))]
    (apply swap! state merge-entity x data' named-parameters)))

(defn- dissoc-in [m path]
  (cond-> m
    (get-in m (butlast path))
    (update-in (butlast path) dissoc (last path))))

(defn- om-ident? [x]
  (and (vector? x)
       (= 2 (count x))
       (keyword? (first x))))

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

(defn vec-remove-index [i v]
  "Remove an item from a vector via index."
  (->> (concat (subvec v 0 i)
               (subvec v (inc i) (count v)))
       (vec)))
