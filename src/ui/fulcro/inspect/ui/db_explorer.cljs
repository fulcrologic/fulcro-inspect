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
    [clojure.string :as str]
    [taoensso.encore :as enc]))

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

(defsc EntityLevel [this {:keys [entity] :as params}]
  {}
  (prim/fragment
    (dom/tr
      (dom/th "Key")
      (dom/th "Value"))
    (if (map? entity)
      (map
        (fn [[k v]]
          (dom/tr {:key (str "entity-key-" k)}
            (dom/td (ui-db-key k))
            (dom/td (ui-db-value params v k))))
        (sort-by (comp key-sort-fn first)
          entity))
      #_else (dom/tr (dom/td "DEBUG PR-STR:" (pr-str entity))))))

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

(letfn [(TABLE [re path table-key]
          (fn [matches entity-key entity]
            (cond-> matches
              (re-find re (str entity))
              (conj {:path (conj path table-key entity-key)
                     :value (ENTITY re entity)}))))
        (ENTITY [re e] (->> e
                         (filter (fn [[_ v]] (re-find re (pr-str v))))
                         (into (empty e))))
        (VALUE [re path matches k v]
          (cond-> matches
            (re-find re (pr-str v))
            (conj {:path (conj path k)
                   :value v})))]
  (defn paths-to-values
    [re state path]
    (reduce-kv
      (fn [matches k v]
        (cond
          (table? v) (reduce-kv (TABLE re path k) matches v)
          :else (VALUE re path matches k v)))
      [] state)))

(defn paths-to-ids [re state]
  (reduce-kv (fn [paths table-key table]
               (if (table? table)
                 (reduce-kv (fn [paths id entity]
                              (cond-> paths
                                (re-find re (pr-str id))
                                (conj {:path [table-key id]})))
                   paths table)
                 paths))
    [] state))

(defn search-for!* [{:as env :keys [state ref]} {:keys [search-query search-type]}]
  (let [props                  (get-in @state ref)
        current-state          (:current-state props)
        internal-fulcro-tables #{:com.fulcrologic.fulcro.components/queries}
        searchable-state       (reduce dissoc current-state internal-fulcro-tables)]
    (h/swap-entity! env assoc :ui/search-results
      (case search-type
        :search/by-value
        (paths-to-values
          (re-pattern search-query)
          searchable-state [])
        :search/by-id
        (paths-to-ids
          (re-pattern search-query)
          searchable-state)))))

(defmutation search-for [search-params]
  (action [env]
    (h/swap-entity! env update :ui/history conj search-params)
    (h/swap-entity! env assoc :ui/path search-params)
    (search-for!* env search-params)))

(defn search-for! [this search-query search-type]
  (let [{::keys [id]} (prim/props this)
        reconciler (prim/any->reconciler this)]
    (prim/transact! reconciler [::id id]
      `[(search-for ~{:search-type  search-type
                      :search-query search-query})])))

(defn pop-history!* [env]
  (h/swap-entity! env update :ui/history (comp vec drop-last)))

(defmutation pop-history [_]
  (action [{:as env :keys [state ref]}]
    (pop-history!* env)
    (let [new-path (last (get-in @state (conj ref :ui/history)))]
      (h/swap-entity! env assoc :ui/path new-path)
      (enc/when-let [search-query (:search-query new-path)
                     search-type  (:search-type new-path)]
        (search-for!* env new-path)
        (h/swap-entity! env assoc :ui/search-query search-query)
        (h/swap-entity! env assoc :ui/search-type search-type)))))

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

(defn ui-db-path* [this {:keys [path search-query]} history]
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
        ">" (dom/button {:disabled (not search-query)
                         :onClick  #(set-path! this path)}
              (str (last path)))))
    (when search-query
      (prim/fragment ">" (dom/button {:disabled true}
                           (str ":search! <" search-query ">"))))))

(defn ui-search-results* [this search-results]
  (prim/fragment
    (dom/tr
      (dom/th "Path")
      (when (some :value search-results)
        (dom/th "Value")))
    ;;TODO: highlight matching sections
    ;;TODO: render with folding
    (map
      (fn [{:keys [path value]}]
        (dom/tr {:key (str "search-path-" path)}
          (dom/td (ui-ident #(set-path! this %) path))
          (when value (dom/td (pr-str value)))))
      (sort-by (comp str :path) search-results))))

(defn mode [{:as props :keys [current-state]}]
  (let [{:keys [path search-query]} (:ui/path props)]
    (cond
      search-query :search
      (empty? path) :top
      (and (= 1 (count path))
        (table? (get-in current-state path))) :table
      :else :entity)))

(defsc DBExplorer [this {:as      props :keys [current-state]
                         :ui/keys [path history search-query search-results search-type]}]
  {:query         [:ui/path :ui/history :ui/search-query :ui/search-results :ui/search-type
                   ::id :current-state]
   :ident         [::id ::id]
   :initial-state {:current-state     {}
                   :ui/search-query   ""
                   :ui/search-results []
                   :ui/search-type    :search/by-value
                   :ui/path           {:path []}
                   :ui/history        []}}
  (let [explorer-mode (mode props)]
    (dom/div {}
      (ui/toolbar {}
        (ui/toolbar-action {}
          (ui/icon {} :search)
          (dom/input {:value       search-query
                      :placeholder "Search DB for:"
                      :onChange    #(m/set-string! this :ui/search-query :event %)
                      :onKeyDown   #(when (= 13 (.-keyCode %))
                                      (search-for! this search-query search-type))})
          (dom/div {}
            (dom/label {}
              (dom/input {:type     "radio"
                          :name     "search_type"
                          :checked  (= search-type :search/by-value)
                          :value    (= search-type :search/by-value)
                          :onChange #(m/set-string! this :ui/search-type
                                       :value :search/by-value)})
              "by Value"))
          (dom/div {}
            (dom/label {}
              (dom/input {:type     "radio"
                          :name     "search_type"
                          :value    (= search-type :search/by-id)
                          :checked  (= search-type :search/by-id)
                          :onChange #(m/set-string! this :ui/search-type
                                       :value :search/by-id)})
              "by ID")))
        (when (= :entity explorer-mode)
          (ui/icon {:onClick #(add-data-watch! this (:path path))} :remove_red_eye)))
      (ui-db-path* this path history)
      ;(dom/div (pr-str history)
      ;(dom/div (pr-str search-results))
      (dom/table {}
        (dom/tbody
          (case explorer-mode
            :search (ui-search-results* this search-results)
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
            :entity (ui-entity-level {:entity       (get-in current-state (:path path))
                                      :addDataWatch (fn [] (add-data-watch! this (:path path)))
                                      :selectMap    (fn [k] (append-to-path! this k))
                                      :selectIdent  (fn [ident] (set-path! this ident))})
            (dom/div "Internal Error")))))))

(def ui-db-explorer (prim/factory DBExplorer))
