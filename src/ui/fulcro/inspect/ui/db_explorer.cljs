(ns fulcro.inspect.ui.db-explorer
  (:require
    [clojure.set :as set]
    [fulcro.client.dom :as dom]
    [fulcro.client.mutations :refer [defmutation]]
    [fulcro.client.primitives :as prim :refer [defsc]]
    [fulcro.inspect.helpers :as h]
    [fulcro.inspect.ui.core :as ui]
    [fulcro.inspect.ui.data-watcher :as dw]
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

(defn compact [s]
  (str/join "."
    (let [segments (str/split s #"\.")]
      (conj
        (mapv first
          (drop-last segments))
        (last segments)))))

(defn ui-db-key [x]
  (if (keyword? x)
    (dom/p ":"
      (when (namespace x)
        (dom/span {:title (namespace x) :style {:color "grey"}}
          (str (compact (namespace x)) "/")))
      (dom/span {:style {:fontWeight "bold"}}
        (name x)))
    (pr-str x)))

(defn ui-db-value [{:keys [selectIdent selectMap]} v k]
  (cond
    (nil? v)
    #_=> "nil"
    (keyword? v)
    #_=> (ui-db-key v)
    (map? v)
    #_=> (ui-ident #(selectMap k) (str "Show " (count v) " ..."))
    (ident? v)
    #_=> (ui-ident selectIdent v)
    (and (vector? v) (every? ident? v))
    #_=> (prim/fragment (map (partial ui-ident selectIdent) v))
    :else (pr-str v)))

(defn key-sort-fn [x]
  (cond-> x
    (or (keyword? x) (symbol? x))
    name))

(defsc EntityLevel [this {:keys [entity addDataWatch] :as params}]
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
      (sort-by (comp key-sort-fn first)
        entity))))

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
      (sort-by key-sort-fn
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
      (sort-by (comp key-sort-fn first)
        tables))
    (dom/tr
      (dom/th "Key")
      (dom/th "Value"))
    (map
      (fn [[k v]]
        (dom/tr {:key (str "top-values-key-" k)}
          (dom/td (ui-db-key k))
          (dom/td (ui-db-value params v k))))
      (sort-by (comp key-sort-fn first)
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

(defn paths-to-matching
  ([re data] (paths-to-matching re data [] []))
  ([re data path] (paths-to-matching re data path []))
  ([re data path matches]
   (-> (cond
         (map? data)
         #_=> (->> data
                (map
                  (fn [[k v]]
                    (paths-to-matching re v (conj path k)
                      (cond-> matches
                        (re-find re (str k))
                        #_=> (conj {:path path :value k})))))
                (apply concat))
         (coll? data)
         #_=> (->> data
                (map-indexed
                  (fn [i x]
                    (paths-to-matching re x (conj path i) matches)))
                (apply concat))
         :else
         #_=> (cond-> matches
                (re-find re (str data))
                #_=> (conj {:path path :value data})))
     (distinct)
     (vec))))

(defn search-for!* [{:as env :keys [state ref]} search]
  (h/swap-entity! env assoc :ui/search-results
    (let [props                  (get-in @state ref)
          current-state          (:current-state props)
          current-path           (-> props :ui/path :path)
          focused-state          (get-in current-state current-path)
          internal-fulcro-tables #{:com.fulcrologic.fulcro.components/queries}
          searchable-state       (reduce #(dissoc %1 %2) focused-state internal-fulcro-tables)]
      (paths-to-matching (re-pattern search)
        searchable-state current-path))))

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
        (h/swap-entity! env assoc :ui/search search))
      (when (not (:search new-path))
        (h/swap-entity! env assoc :ui/search "")))))

(defn pop-history! [this]
  (let [{::keys [id]} (prim/props this)
        reconciler (prim/any->reconciler this)]
    (prim/transact! reconciler [::id id]
      `[(pop-history {})])))

(defn add-data-watch! [this path]
  (let [{::keys [id]} (prim/props this)
        reconciler (prim/any->reconciler this)]
    (prim/transact! reconciler [::id id]
      `[(dw/add-data-watch
          ~{:path path})])))

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
  (let [{:keys [selectTopKey]} (prim/get-state this)
        explorer-mode (mode props)]
    (dom/div {}
      (ui-db-path this path history)
      (dom/div
        (dom/input {:value     search
                    :onChange  #(m/set-string! this :ui/search :event %)
                    :onKeyDown #(when (= 13 (.-keyCode %))
                                  (search-for! this search))})
        (ui/icon {:onClick #(clear-search! this)} :cancel)
        (when (= :entity explorer-mode)
          (ui/icon {:onClick #(add-data-watch! this (:path path))} :remove_red_eye)))
      ;(pr-str history)
      (dom/table {}
        (dom/tbody
          (case explorer-mode
            :search (prim/fragment
                      (dom/tr
                        (dom/th "Path")
                        (dom/th "Value"))
                      (map
                        (fn [{:keys [path value]}]
                          (dom/tr {:key (str "search-path-" path)}
                            (dom/td (ui-ident #(set-path! this %) path))
                            (dom/td (str value))))
                        search-results))
            :top (let [top-keys    (set (keys current-state))
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
                                      :addDataWatch (fn [] (add-data-watch! this (:path path)))
                                      :selectMap   (fn [k] (append-to-path! this k))
                                      :selectIdent (fn [ident] (set-path! this ident))})
            (dom/div "Internal Error")))))))

(def ui-db-explorer (prim/factory DBExplorer))
