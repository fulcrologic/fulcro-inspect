(ns fulcro.inspect.ui.db-explorer
  (:require
    [clojure.set :as set]
    [fulcro.client.dom :as dom]
    [fulcro.client.mutations :refer [defmutation]]
    [fulcro.client.primitives :as prim :refer [defsc]]
    [fulcro.inspect.helpers :as h]
    [fulcro.inspect.ui.core :as ui]
    [fulcro.util :refer [ident?]]
    [fulcro.client.mutations :as m]
    [clojure.string :as str]))

(defmutation set-current-state [new-state]
  (action [env]
    (h/swap-entity! env assoc :current-state new-state)))

(defn table? [v]
  (and
    (map? v)
    (every? map? (vals v))))

(defn ui-ident [f v]
  (dom/div (dom/button {:key (str "ident-" v) :onClick #(f v)} (str v))))

(defn ui-db-value [{:keys [selectIdent selectMap]} v k]
  (cond
    (ident? v)
    #_=> (ui-ident selectIdent v)
    (and (vector? v) (every? ident? v))
    #_=> (prim/fragment (map (partial ui-ident selectIdent) v))
    (map? v)
    #_=> (ui-ident #(selectMap k) (str "Show " (count v) " ..."))
    (nil? v) "nil"
    :else (str v)))

(defn compact [s]
  (str/join "."
    (let [segments (str/split s #"\.")]
      (conj
        (mapv first
          (drop-last segments))
        (last segments)))))

(defn ui-db-key [x]
  (cond
    (or (keyword? x) (symbol? x))
    #_=> (str (if (keyword? x) ":" "'") (name x) (if (not (namespace x)) "" (str " \\ " (compact (namespace x)))))
    (and (string? x) (re-find #".+\..+/" x))
    #_=> (apply str (reverse (update (str/split x #"/") 0 #(->> % compact (str " \\ ")))))
    :else (str x)))

(defsc EntityLevel [this {:keys [entity] :as params}]
  {}
  (prim/fragment
    (dom/tr
      (dom/th "Key")
      (dom/th "Value"))
    (map
      (fn [[k v]]
        (dom/tr {:key (str "entity-key-" k)}
          (dom/td (ui-db-key k))
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
              (ui-db-key entity-id)))))
      (sort-by ui-db-key
        entity-ids))))

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
          (dom/td (ui-db-key k))
          (dom/td
            (dom/button {:onClick #(selectTopKey k)}
              (str "Show " (count v) " ...")))))
      (sort-by (comp ui-db-key first)
        tables))
    (dom/tr
      (dom/th "Key")
      (dom/th "Value"))
    (map
      (fn [[k v]]
        (dom/tr {:key (str "top-values-key-" k)}
          (dom/td (ui-db-key k))
          (dom/td (ui-db-value params v k))))
      (sort-by (comp ui-db-key first)
        root-values))))

(def ui-top-level (prim/factory TopLevel))

(defn set-path!* [env path]
  (h/swap-entity! env assoc :ui/path {:path path})
  (h/swap-entity! env update :ui/history conj {:path path}))

(defmutation set-path [{:keys [path]}]
  (action [env]
    (set-path!* env path)))

(defn set-path! [this path]
  (let [{::keys [id]} (prim/props this)
        reconciler (prim/any->reconciler this)]
    (prim/transact! reconciler [::id id]
      `[(set-path {:path ~path})])))

(defmutation append-to-path [{:keys [sub-path]}]
  (action [{:as env :keys [state ref]}]
    (h/swap-entity! env update-in [:ui/path :path] into sub-path)
    (h/swap-entity! env update :ui/history conj
      {:path (get-in @state (conj ref :ui/path :path))})))

(defn append-to-path! [this & sub-path]
  (let [{::keys [id]} (prim/props this)
        reconciler (prim/any->reconciler this)]
    (prim/transact! reconciler [::id id]
      `[(append-to-path {:sub-path ~sub-path})])))

(defn path-walk [f path e]
  (let [e' (f path e)]
    (cond
      (map? e')  (->> e'
                   (map (fn [[k x]] [k (path-walk f (conj path k) x)]))
                   (into (empty e')))
      (coll? e') (->> e'
                   (map-indexed (fn [i x] (path-walk f (conj path i) x)))
                   (into (empty e')))
      :else      e')))

(defn search-for!* [{:as env :keys [state ref]} search]
  (h/swap-entity! env assoc :ui/search-results
    (let [paths (atom [])]
      (path-walk (fn [path x]
                   (when (and (not (coll? x))
                           (re-find (re-pattern search) (str x)))
                     (swap! paths conj {:path path :value x}))
                   x)
        [] (let [{:as props :keys [current-state]} (get-in @state ref)]
             (get-in current-state
               (-> props :ui/path :path))))
      @paths)))

(defmutation search-for [{:keys [search]}]
  (action [{:as env :keys [state ref]}]
    (h/swap-entity! env update :ui/history conj
      (merge
        (get-in @state (conj ref :ui/path))
        {:search search}))
    (h/swap-entity! env assoc-in [:ui/path :search] search)
    (search-for!* env search)))

(defn search-for! [this search]
  (let [{::keys [id]} (prim/props this)
        reconciler (prim/any->reconciler this)]
    (prim/transact! reconciler [::id id]
      `[(search-for ~{:search search})])))

(defn pop-history!* [env]
  (h/swap-entity! env update :ui/history (comp vec drop-last)))

(defmutation clear-search [_]
  (action [env]
    (h/swap-entity! env assoc :ui/search "")
    (h/swap-entity! env update :ui/path dissoc :search)
    (pop-history!* env)))

(defn clear-search! [this]
  (let [{::keys [id]} (prim/props this)
        reconciler (prim/any->reconciler this)]
    (prim/transact! reconciler [::id id]
      `[(clear-search ~{})])))

(defmutation pop-history [_]
  (action [{:as env :keys [state ref]}]
    (pop-history!* env)
    (let [new-path (last (get-in @state (conj ref :ui/history)))]
      (h/swap-entity! env assoc :ui/path new-path)
      (when-let [search (:search new-path)]
        (search-for!* env search)
        (h/swap-entity! env assoc :ui/search search)))))

(defn pop-history! [this]
  (let [{::keys [id]} (prim/props this)
        reconciler (prim/any->reconciler this)]
    (prim/transact! reconciler [::id id]
      `[(pop-history {})])))

(defn ui-db-path [this {:keys [path search]} history]
  {}
  (dom/div {}
    (dom/button {:onClick  #(pop-history! this)
                 :disabled (empty? history)} "<")
    (dom/button {:onClick #(set-path! this [])} "TOP")
    (when (seq (drop-last path))
      (map
        (fn [sub-path]
          (prim/fragment {:key (str "db-path-" sub-path)}
            ">" (dom/button {:onClick #(set-path! this sub-path)}
                  (str (last sub-path)))))
        (let [[x & xs] (drop-last path)]
          (reductions conj [x] xs))))
    (when (last path)
      (prim/fragment
        ">" (dom/button {:disabled (not search)
                         :onClick  #(set-path! this path)}
              (str (last path)))))
    (when search
      (prim/fragment ">" (dom/button {:disabled true} (str ":search \"" search \"))))))

(defn mode [{:as props :keys [current-state]}]
  (let [{:keys [path search]} (:ui/path props)]
    (cond
      search :search
      (empty? path) :top
      (and (= 1 (count path))
        (table? (get-in current-state path))) :table
      :else :entity)))

(defsc DBExplorer [this {:as props :ui/keys [path history search search-results] :keys [current-state]}]
  {:query         [:ui/path :ui/history :ui/search :ui/search-results ::id :current-state]
   ;   :initLocalState (fn [] {:selectTopKey (fn [k] (select-top-key this k))})
   :ident         [::id ::id]
   :initial-state {:current-state     {}
                   :ui/search         ""
                   :ui/search-results []
                   :ui/path           {:path []}
                   :ui/history        []}}
  (let [{:keys [selectTopKey]} (prim/get-state this)]
    (dom/div {}
      (ui-db-path this path history)
      (dom/div
        (dom/input {:value     search
                    :onChange  #(m/set-string! this :ui/search :event %)
                    :onKeyDown #(when (= 13 (.-keyCode %))
                                  (search-for! this search))})
        (ui/icon {:onClick #(clear-search! this)}
          :cancel))
      ;(pr-str history)
      (dom/table {}
        (dom/tbody
          (case (mode props)
            :search (prim/fragment
                      (dom/tr
                        (dom/th "Path")
                        (dom/th "Value"))
                      (map
                        (fn [{:keys [path value]}]
                          (dom/tr {:key (str "search-path-" path)}
                            (dom/td (ui-ident #(set-path! this (drop-last %)) path))
                            (dom/td (str value))))
                        search-results))
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
            :table (ui-table-level {:entity-ids   (keys (get-in current-state (:path path)))
                                    :selectEntity (fn [id] (append-to-path! this id))})
            :entity (ui-entity-level {:entity      (get-in current-state (:path path))
                                      :selectMap   (fn [k] (append-to-path! this k))
                                      :selectIdent (fn [ident] (set-path! this ident))})
            (dom/div "Internal Error")))))))

(def ui-db-explorer (prim/factory DBExplorer))
