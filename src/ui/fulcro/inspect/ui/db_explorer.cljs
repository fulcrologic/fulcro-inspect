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

(defmutation set-current-state [new-state]
  (action [env]
    (h/swap-entity! env assoc :current-state new-state)))

(defn table? [v]
  (and
    (map? v)
    (every? map? (vals v))))

(defn ui-ident [f v]
  (dom/button {:key (str "ident-" v) :onClick #(f v)} (str v)))

(defn ui-db-value [{:keys [selectIdent selectMap]} v k]
  (cond
    (ident? v)
    #_=> (ui-ident selectIdent v)
    (and (vector? v) (every? ident? v) (vector? v))
    #_=> (prim/fragment (map (partial ui-ident selectIdent) v))
    (map? v)
    #_=> (ui-ident #(selectMap k) (str "Show " (count v) " ..."))
    (nil? v) "nil"
    :else (str v)))

(defsc EntityLevel [this {:keys [entity] :as params}]
  {}
  (prim/fragment
    (dom/tr
      (dom/th "Key")
      (dom/th "Value"))
    (map
      (fn [[k v]]
        (dom/tr {:key (str "entity-key-" k)}
          (dom/td (str k))
          (dom/td (ui-db-value params v k))))
      entity)))

(def ui-entity-level (prim/factory EntityLevel))

(defsc TableLevel [this {:keys [entity-ids selectEntity]}]
  {}
  (prim/fragment
    (dom/tr
      (dom/th "Entity ID"))
    (map
      (fn [entity-id]
        (dom/tr {:key (str "table-key-" entity-id)}
          (dom/td
            (dom/button {:onClick #(selectEntity entity-id)}
              (str entity-id)))))
      entity-ids)))

(def ui-table-level (prim/factory TableLevel))

(defsc TopLevel [this {:keys [tables root-values selectTopKey] :as params}]
  {}
  (prim/fragment
    (dom/tr
      (dom/th "Table")
      (dom/th "Entities"))
    (map
      (fn [[k v]]
        (dom/tr {:key (str "top-tables-key-" k)}
          (dom/td (str k))
          (dom/td
            (dom/button {:onClick #(selectTopKey k)}
              (str "Show " (count v) " ...")))))
      (sort tables))
    (dom/tr
      (dom/th "Key")
      (dom/th "Value"))
    (map
      (fn [[k v]]
        (dom/tr {:key (str "top-values-key-" k)}
          (dom/td (str k))
          (dom/td (ui-db-value params v k))))
      root-values)))

(def ui-top-level (prim/factory TopLevel))

(defmutation set-path [{:keys [path]}]
  (action [env]
    (h/swap-entity! env assoc :ui/path path)
    (h/swap-entity! env update :ui/history conj path)))

(defn set-path! [this path]
  (let [{::keys [id]} (prim/props this)
        reconciler (prim/any->reconciler this)]
    (prim/transact! reconciler [::id id]
      `[(set-path {:path ~path})])))

(defmutation append-to-path [{:keys [sub-path]}]
  (action [{:as env :keys [state ref]}]
    (h/swap-entity! env update :ui/path into sub-path)
    (h/swap-entity! env update :ui/history conj
      (get-in @state (conj ref :ui/path)))))

(defn append-to-path! [this & sub-path]
  (let [{::keys [id]} (prim/props this)
        reconciler (prim/any->reconciler this)]
    (prim/transact! reconciler [::id id]
      `[(append-to-path {:sub-path ~sub-path})])))

(defmutation pop-history [_]
  (action [{:as env :keys [state ref]}]
    (h/swap-entity! env update :ui/history (comp vec drop-last))
    (h/swap-entity! env assoc :ui/path (last (get-in @state (conj ref :ui/history))))))

(defn pop-history! [this]
  (let [{::keys [id]} (prim/props this)
        reconciler (prim/any->reconciler this)]
    (prim/transact! reconciler [::id id]
      `[(pop-history {})])))

(defn ui-db-path [this path]
  {}
  (dom/div {}
    (dom/button {:onClick #(pop-history! this)} "<")
    (dom/button {:onClick #(set-path! this [])} "TOP")
    (when (seq (drop-last path))
      (map
        (fn [sub-path]
          (prim/fragment
            ">" (dom/button {:onClick #(set-path! this sub-path)}
                  (str (last sub-path)))))
        (let [[x & xs] (drop-last path)]
          (reductions conj [x] xs))))
    (when (last path)
      (prim/fragment
        ">" (dom/button {:disabled true}
              (str (last path)))))))

(defsc DBExplorer [this {:ui/keys [path history] :keys [current-state]}]
  {:query         [:ui/path :ui/history ::id :current-state]
   ;   :initLocalState (fn [] {:selectTopKey (fn [k] (select-top-key this k))})
   :ident         [::id ::id]
   :initial-state {:current-state {}
                   :ui/path []
                   :ui/history []}}
  (let [{:keys [selectTopKey]} (prim/get-state this)
        mode (cond
               (empty? path) :top
               (and (= 1 (count path))
                 (table? (get-in current-state path))) :table
               :else :entity)]
    (dom/div {}
      ;(pr-str history)
      (ui-db-path this path)
      (dom/table {}
        (dom/tbody
          (case mode
            :top (let [top-keys    (set (sort (keys current-state)))
                       tables      (filter
                                     (fn [k]
                                       (let [v (get current-state k)]
                                         (table? v)))
                                     top-keys)
                       root-values (select-keys current-state (set/difference top-keys (set tables)))]
                   (ui-top-level {:tables       (select-keys current-state tables)
                                  :root-values  root-values
                                  :selectIdent  (fn [ident] (set-path! this ident))
                                  :selectMap    (fn [k] (append-to-path! this k))
                                  :selectTopKey (fn [k] (set-path! this [k]))}))
            :table (ui-table-level {:entity-ids   (keys (get-in current-state path))
                                    :selectEntity (fn [id] (append-to-path! this id))})
            :entity (ui-entity-level {:entity      (get-in current-state path)
                                      :selectMap   (fn [k] (append-to-path! this k))
                                      :selectIdent (fn [ident] (set-path! this ident))})
            (dom/div "Internal Error")))))))

(def ui-db-explorer (prim/factory DBExplorer))
