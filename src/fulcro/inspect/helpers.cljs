(ns fulcro.inspect.helpers
  (:require [fulcro.client.core :as fulcro]
            [om.next :as om]))

(defn query-component
  ([this]
   (let [component (om/react-type this)
         ref       (om/get-ident this)]
     (-> (om/transact! this [{ref (om/get-query component)}])
         (get ref))))
  ([this focus-path]
   (let [component (om/react-type this)
         ref       (om/get-ident this)]
     (-> (om/transact! this [{ref (om/focus-query (om/get-query component) focus-path)}])
         (get-in (concat [ref] focus-path))))))

(defn swap-entity! [{:keys [state ref]} & args]
  (apply swap! state update-in ref args))

(defn merge-entity [state x data & named-parameters]
  "Starting from a denormalized entity map, normalizes using class x.
   It assumes the entity is going to be normalized too, then get all
   normalized data and merge back into the app state and idents."
  (let [idents     (-> (om/tree->db
                         (reify
                           om/IQuery
                           (query [_] [{::root (om/get-query x)}]))
                         {::root data} true)
                       (dissoc ::root ::om/tables))
        root-ident (om/ident x data)
        state      (merge-with (partial merge-with merge) state idents)]
    (if (seq named-parameters)
      (apply fulcro/integrate-ident state root-ident named-parameters)
      state)))

(defn create-entity! [{:keys [state ref]} x data & named-parameters]
  (let [named-parameters (->> (partition 2 named-parameters)
                              (map (fn [[op path]] [op (conj ref path)]))
                              (apply concat))
        data'            (if (-> data meta ::initialized)
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

(defn remove-all [{:keys [state ref]} field]
  (let [children (get-in @state (conj ref field))]
    (if (seq children)
      (swap! state (comp #(assoc-in % (conj ref field) [])
                         #(reduce deep-remove-ref % children))))))

(defn vec-remove-index [i v]
  "Remove an item from a vector via index."
  (->> (concat (subvec v 0 i)
               (subvec v (inc i) (count v)))
       (vec)))
