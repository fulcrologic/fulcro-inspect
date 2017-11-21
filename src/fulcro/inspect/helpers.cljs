(ns fulcro.inspect.helpers
  (:require [fulcro.client.core :as fulcro]
            [om.next :as om]))

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
        state      (as-> state <>
                     (update-in <> root-ident merge root)
                     (merge-with (partial merge-with merge) <> idents))]
    (if (seq named-parameters)
      (apply fulcro/integrate-ident state root-ident named-parameters)
      state)))

(defn create-entity! [{:keys [state ref]} x data & named-parameters]
  (let [named-parameters (->> (partition 2 named-parameters)
                              (map (fn [[op path]] [op (conj ref path)]))
                              (apply concat))]
    (apply swap! state merge-entity x (fulcro/get-initial-state x data) named-parameters)))

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
