(ns fulcro.inspect.ui.db-explorer
  (:require
    [clojure.pprint :refer [pprint]]
    [clojure.set :as set]
    [fulcro.client.dom :as dom]
    [fulcro.client.mutations :refer [defmutation]]
    [fulcro.client.primitives :as prim :refer [defsc]]
    [fulcro.inspect.helpers :as h]
    [fulcro.inspect.ui.core :as ui]
    [fulcro.util :refer [ident?]]))

(defmutation set-content [new-state]
  (action [env]
    (h/swap-entity! env assoc :current-database new-state)))

(defsc EntityLevel [this {:keys [entity selectIdent]}]
  {}
  (prim/fragment
    (dom/tr
      (dom/th "Key")
      (dom/th "Value"))
    (map
      (fn [[k v]]
        (dom/tr
          (dom/td (str k))
          (dom/td
            (cond
              (ident? v) (dom/button {:onClick #(when selectIdent (selectIdent v))}
                           (str v))
              (nil? v) "nil"
              :else (str v)))))
      entity)))

(def ui-entity-level (prim/factory EntityLevel))

(defsc TableLevel [this {:keys [entity-ids selectEntity]}]
  {}
  (prim/fragment
    (dom/tr
      (dom/th "Entity ID"))
    (map
      (fn [entity-id]
        (dom/tr
          (dom/td
            (dom/button {:onClick #(when selectEntity (selectEntity entity-id))}
              (pr-str entity-id)))))
      entity-ids)))

(def ui-table-level (prim/factory TableLevel))

(defsc TopLevel [this {:keys [tables root-values selectTopKey]}]
  {}
  (prim/fragment
    (dom/tr
      (dom/th "Table")
      (dom/th "Entities"))
    (map
      (fn [[k v]]
        (dom/tr
          (dom/td (str k))
          (dom/td
            (dom/button {:onClick #(when selectTopKey (selectTopKey k))}
              (str "Show " (count v) " ...")))))
      (sort tables))
    (dom/tr
      (dom/th "Key")
      (dom/th "Value"))
    (map
      (fn [[k v]]
        (dom/tr
          (dom/td (str k))
          (dom/td (str v))))
      root-values)))

(def ui-top-level (prim/factory TopLevel))

(defmutation set-path [{:keys [path]}]
  (action [env]
    (h/swap-entity! env assoc :ui/path path)))

(defn set-path! [this path]
  (let [{::keys [id]} (prim/props this)
        reconciler (prim/any->reconciler this)]
    (prim/transact! reconciler [::id id] `[(set-path {:path ~path})])))

(defsc DBExplorer [this {:ui/keys [path] :keys [current-database]}]
  {:query         [:ui/path ::id :current-database]
   ;   :initLocalState (fn [] {:selectTopKey (fn [k] (select-top-key this k))})
   :ident         [::id ::id]
   :initial-state {:current-database {}}}
  (let [{:keys [selectTopKey]} (prim/get-state this)
        mode (cond
               (empty? path) :top
               (= 1 (count path)) :table
               (= 2 (count path)) :entity)]
    (dom/div {}
      (case mode
        :top (dom/div {}
               (dom/button "TOP"))
        :table (dom/div {}
                 (dom/button {:onClick #(set-path! this [])}
                   "TOP")
                 ">"
                 (dom/button {:disabled true}
                   (str (first path))))
        :entity (dom/div {}
                  (dom/button {:onClick #(set-path! this [])}
                    "TOP")
                  ">"
                  (dom/button {:onClick #(set-path! this [(first path)])}
                    (str (first path)))
                  ">"
                  (dom/button {:disabled true}
                    (str (second path)))))
      ;(ui/icon :remove_red_eye)
      (dom/table {}
        (dom/tbody
          (case mode
            :top (let [top-keys    (set (sort (keys current-database)))
                       tables      (filter
                                     (fn [k]
                                       (let [v (get current-database k)]
                                         (and
                                           (map? v)
                                           (every? map? (vals v)))))
                                     top-keys)
                       root-values (select-keys current-database (set/difference top-keys (set tables)))]
                   (ui-top-level {:tables       (select-keys current-database tables)
                                  :root-values  root-values
                                  :selectTopKey (fn [k] (set-path! this [k]))}))
            :table (ui-table-level {:entity-ids   (or
                                                    (sort (keys (get-in current-database path)))
                                                    [])
                                    :selectEntity (fn [id]
                                                    (set-path! this (conj path id)))})
            :entity (ui-entity-level {:entity (get-in current-database path)
                                      :selectIdent (fn [ident]
                                                     (set-path! this ident))})
            (dom/div "Internal Error")))))))

(def ui-db-explorer (prim/factory DBExplorer))
