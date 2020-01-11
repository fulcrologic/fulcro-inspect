(ns fulcro.inspect.ui.db-explorer
  (:require
    [clojure.pprint :refer [pprint]]
    [fulcro.client.primitives :as prim :refer [defsc]]
    [fulcro.client.mutations :refer [defmutation]]
    [fulcro.client.dom :as dom]
    [fulcro.inspect.helpers :as h]))

(defmutation set-content [new-state]
  (action [env]
    (h/swap-entity! env assoc :current-database new-state)))

(defsc EntityLevel [this {:keys [path entity]}]
  {}
  (dom/div
    {}
    (dom/pre {}
      (with-out-str
        (pprint entity)))))

(def ui-entity-level (prim/factory EntityLevel))

(defsc TableLevel [this {:keys [entity-ids selectEntity]}]
  {}
  (map-indexed
    (fn [idx entity-id]
      (dom/div {:key (str idx)}
        (dom/button {:onClick #(when selectEntity (selectEntity entity-id))}
          (pr-str entity-id))))
    entity-ids))

(def ui-table-level (prim/factory TableLevel))

(defsc TopLevel [this {:keys [top-keys selectTopKey]}]
  {}
  (map-indexed
    (fn [idx k]
      (dom/div {:key (str idx)}
        (dom/button {:onClick #(when selectTopKey (selectTopKey k))}
          (pr-str k))))
    top-keys))

(def ui-top-level (prim/factory TopLevel))

(defmutation set-path [{:keys [path]}]
  (action [env]
    (h/swap-entity! env assoc :ui/path path)))

(defn set-path! [this path]
  (let [{::keys [id]} (prim/props this)
        reconciler (prim/any->reconciler this)]
    (prim/transact! reconciler [::id id] `[(set-path {:path ~path})])))

(defsc DBExplorer [this {:ui/keys [path]
                         ::keys   [id]
                         :keys    [current-database]}]
  {:query         [:ui/path
                   ::id
                   :current-database]
   ;   :initLocalState (fn [] {:selectTopKey (fn [k] (select-top-key this k))})
   :ident         [::id ::id]
   :initial-state {:current-database {}}}
  (let [{:keys [selectTopKey]} (prim/get-state this)
        mode (cond
               (empty? path) :top
               (= 1 (count path)) :table
               (= 2 (count path)) :entity)]
    (dom/div {}
      (map-indexed (fn [idx k] (dom/button {:key idx} (str k))) (into ["TOP"] path))
      (case mode
        :top (let [top-keys (sort (keys current-database))]
               (ui-top-level {:top-keys     top-keys
                              :selectTopKey (fn [k] (set-path! this [k]))}))
        :table (ui-table-level {:entity-ids   (or
                                                (sort (keys (get-in current-database path)))
                                                [])
                                :selectEntity (fn [id]
                                                (set-path! this (conj path id)))})
        :entity (ui-entity-level {:x      (random-uuid)
                                  :entity (get-in current-database path)})
        (dom/div "Internal Error")))))

(def ui-db-explorer (prim/factory DBExplorer))
