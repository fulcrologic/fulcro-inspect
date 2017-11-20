(ns fulcro.inspect.helpers
  (:require [om.next :as om]))

(defn merge-entity [state x data]
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
        idents     (dissoc idents ::root ::om/tables)]
    (as-> state <>
      (update-in <> root-ident merge root)
      (merge-with (partial merge-with merge) <> idents))))

(defn vec-remove-index [i v]
  (->> (concat (subvec v 0 i)
               (subvec v (inc i) (count v)))
       (vec)))
