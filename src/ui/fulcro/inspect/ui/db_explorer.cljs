(ns fulcro.inspect.ui.db-explorer
  (:require
    [clojure.set :as set]
    [clojure.pprint :refer [pprint]]
    [fulcro.client.dom :as dom :refer [div button label input span a]]
    [fulcro.client.mutations :refer [defmutation]]
    [fulcro.client.primitives :as prim :refer [defsc]]
    [fulcro.inspect.helpers :as h]
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

(defn compact [s]
  (str/join "."
    (let [segments (str/split s #"\.")]
      (conj
        (mapv first
          (drop-last segments))
        (last segments)))))

(defn compact-keyword [kw]
  (if (and (keyword? kw) (namespace kw))
    (keyword
      (compact (namespace kw))
      (name kw))
    kw))

(defn ui-ident [f v]
  (let [ident    (mapv compact-keyword v)
        segments (str/split (str ident) #"\s")]
    (div {:key (str "ident-" v)}
      (a {:href    "#"
          :title   (str v)
          :onClick #(f v)}
        (map #(span {:key   (str "ident-segment-" v "-" %)
                     :style {:whiteSpace "nowrap"}}
                (str " " %))
          segments)))))

(defn ui-db-key [selectIdent x]
  (cond
    (keyword? x)
    #_=> (span {:style {:whiteSpace "nowrap"}} ":"
           (when (namespace x)
             (span {:title (namespace x) :style {:color "grey"}}
               (str (compact (namespace x)) "/")))
           (span {:style {:fontWeight "bold"}}
             (name x)))
    (ident? x) (ui-ident selectIdent x)
    :else (span {:style {:whiteSpace "nowrap"}} (pr-str x))))

(defn ui-db-value [{:keys [selectIdent selectMap]} v k]
  (dom/pre {}
    (cond
      (nil? v)
      #_=> "nil"
      (keyword? v)
      #_=> (ui-db-key selectIdent v)
      (map? v)
      #_=> (a {:href "#" :onClick #(selectMap k)} (str (count (keys v)) " items"))
      (ident? v)
      #_=> (ui-ident selectIdent v)
      (and (vector? v) (every? ident? v))
      #_=> (prim/fragment (map (partial ui-ident selectIdent) v))
      :else (pr-str v))))

(defn key-sort-fn [x]
  (if (or (keyword? x) (symbol? x))
    (name x)
    (str x)))

(defsc EntityLevel [this {:keys [selectIdent entity] :as params}]
  {}
  (prim/fragment
    (dom/tr
      (dom/th "Key")
      (dom/th "Value"))
    (if (map? entity)
      (map
        (fn [[k v]]
          (dom/tr {:key (str "entity-key-" k)}
            (dom/td (ui-db-key selectIdent k))
            (dom/td (ui-db-value params v k))))
        (sort-by (comp key-sort-fn first)
          entity))
      #_else (dom/tr (dom/td "DEBUG PR-STR:" (pr-str entity))))))

(def ui-entity-level (prim/factory EntityLevel))

(defsc TableLevel [this {:keys [selectIdent entity-ids selectEntity]}]
  {}
  (prim/fragment
    (dom/tr
      (dom/th "Entity ID"))
    (map
      (fn [entity-id]
        (dom/tr {:key (str "table-key-" entity-id)}
          (dom/td
            (a {:href "#" :onClick #(selectEntity entity-id)}
              (ui-db-key selectIdent entity-id)))))
      (sort-by key-sort-fn
        entity-ids))))

(def ui-table-level (prim/factory TableLevel))

(defsc TopLevel [this {:keys [selectIdent tables root-values selectTopKey] :as params}]
  {}
  (prim/fragment
    (dom/tr {:colSpan "2"}
      (dom/th (dom/h2 :.ui.header
                {:style {:marginTop    "0.5rem"
                         :marginBottom "0.5rem"}}
                "Tables")))
    (dom/tr
      (dom/th "Table")
      (dom/th "Entities"))
    (map
      (fn [[k v]]
        (dom/tr {:key (str "top-tables-key-" k)}
          (dom/td (ui-db-key selectIdent k))
          (dom/td
            (a {:href "#" :onClick #(selectTopKey k)}
              (str (count (keys v)) " items")))))
      (sort-by key tables))
    (dom/tr {:colSpan "2"}
      (dom/th
        (dom/h2 :.ui.header
          {:style {:marginTop    "0.5rem"
                   :marginBottom "0.5rem"}}
          "Top-Level Keys")))
    (dom/tr
      (dom/th "Key")
      (dom/th "Value"))
    (map
      (fn [[k v]]
        (dom/tr {:key (str "top-values-key-" k)}
          (dom/td (ui-db-key selectIdent k))
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
              (conj {:path  (conj path table-key entity-key)
                     :value (ENTITY re entity)}))))
        (ENTITY [re e] (->> e
                         (filter (fn [[_ v]] (re-find re (pr-str v))))
                         (into (empty e))))
        (VALUE [re path matches k v]
          (cond-> matches
            (re-find re (pr-str v))
            (conj {:path  (conj path k)
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

(defn search-for! [this search-query [shift-key? search-type]]
  (let [{::keys [id]} (prim/props this)
        reconciler         (prim/any->reconciler this)
        invert-search-type {:search/by-id    :search/by-value
                            :search/by-value :search/by-id}]
    (prim/transact! reconciler [::id id]
      `[(search-for ~{:search-type  (cond-> search-type
                                      shift-key? invert-search-type)
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
      `[(dw/add-data-watch ~{:path path})])))

(defn ui-db-path* [this {:keys [path search-query]} history]
  (prim/fragment
    (div :.ui.large.breadcrumb
      (a :.section {:onClick #(set-path! this [])} "Top")
      (when (seq (drop-last path))
        (map
          (fn [sub-path]
            (prim/fragment {:key (str "db-path-" sub-path)}
              (dom/i :.right.angle.icon.divider)
              (a :.section {:onClick #(set-path! this sub-path)
                            :title   (str (last sub-path))}
                (str (compact-keyword (last sub-path))))))
          (let [[x & xs] (drop-last path)]
            (reductions conj [x] xs))))
      (when (last path)
        (prim/fragment
          (dom/i :.right.angle.icon.divider)
          (a :.active.section {:disabled (not search-query)
                               :title    (str (last path))
                               :onClick  #(set-path! this path)}
            (str (compact-keyword (last path))))))
      (when search-query
        (prim/fragment
          (dom/i :.right.angle.icon.divider)
          (a :.active.section {:disabled true}
            (str "\"" search-query "\"")))))))

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
          (dom/td {}
            (ui-ident #(set-path! this %) path))
          (when value (dom/td (dom/pre
                                (with-out-str
                                  (pprint value)))))))
      (sort-by (comp str :path) search-results))))

(defn mode [{:as props :keys [current-state]}]
  (let [{:keys [path search-query]} (:ui/path props)]
    (cond
      search-query :search
      (empty? path) :top
      (and (= 1 (count path))
        (table? (get-in current-state path))) :table
      :else :entity)))

(defn ui-toolbar* [this {:ui/keys [history search-query search-type]}]
  (div :.ui.form
    (div :.inline.fields {:style {:marginBottom "0"}}
      (div :.ui.buttons
        (button :.ui.icon.button
          {:onClick  #(pop-history! this)
           :disabled (empty? history)}
          (dom/i :.left.arrow.icon)))
      (div :.ui.icon.input
        (input {:value       search-query
                :placeholder "Search DB for:"
                :onChange    #(m/set-string! this :ui/search-query :event %)
                :onKeyDown   #(when (= 13 (.-keyCode %))
                                (search-for! this search-query
                                  [(.-shiftKey %) search-type]))})
        (dom/i :.search.icon.link
          {:onClick #(search-for! this search-query
                       [(.-shiftKey %) search-type])}))
      (div :.ui.buttons
        (button :.ui.button.toggle
          {:className (if (= search-type :search/by-value) "active" "basic")
           :onClick   #(m/set-string! this :ui/search-type
                         :value :search/by-value)}
          "by Value")
        (button :.ui.button.toggle
          {:className (if (= search-type :search/by-id) "active" "basic")
           :onClick   #(m/set-string! this :ui/search-type
                         :value :search/by-id)}
          "by ID")))))

(defsc DBExplorer [this {:as      props :keys [current-state]
                         :ui/keys [path history search-results]}]
  {:query         [:ui/path :ui/history :ui/search-query :ui/search-results :ui/search-type
                   ::id :current-state]
   :ident         [::id ::id]
   :initial-state {:current-state     {}
                   :ui/search-query   ""
                   :ui/search-results []
                   :ui/search-type    :search/by-value
                   :ui/path           {:path []}
                   :ui/history        []}}
  (try
    (let [explorer-mode (mode props)]
      (div
        (ui-toolbar* this props)
        ;(div (pr-str history)
        ;(div (pr-str search-results))
        (div :.ui.container {:style {:marginLeft "0.5rem"}}
          (ui-db-path* this path history)
          (when (= :entity explorer-mode)
            (button :.ui.tertiary.button.animated.icon
              {:onClick #(add-data-watch! this (:path path))}
              (div :.visible.content (dom/i :.icon.eye))
              (div :.hidden.content "Watch")))
          (dom/table :.ui.compact.celled.fluid.table {:style {:marginTop "0"}}
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
                                        :selectIdent  (fn [ident] (set-path! this ident))
                                        :selectEntity (fn [id] (append-to-path! this id))})
                :entity (ui-entity-level {:entity      (get-in current-state (:path path))
                                          :selectMap   (fn [k] (append-to-path! this k))
                                          :selectIdent (fn [ident] (set-path! this ident))})
                (div "Internal Error")))))))
    (catch :default e
      (dom/div (str "Inspect rendering threw an exception. Start a new tab to try again. Report an issue. Excection:"
                 (ex-message e))))))

(def ui-db-explorer (prim/factory DBExplorer))
