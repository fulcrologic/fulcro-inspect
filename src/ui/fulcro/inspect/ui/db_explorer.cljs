(ns fulcro.inspect.ui.db-explorer
  (:require
    [clojure.pprint :refer [pprint]]
    [clojure.set :as set]
    [clojure.string :as str]
    [fulcro.client.localized-dom :as dom :refer [div button label input span a]]
    [fulcro.client.mutations :as fm :refer [defmutation]]
    [fulcro.client.primitives :as fc :refer [defsc]]
    [fulcro.inspect.lib.history :as hist]
    [fulcro.inspect.helpers :as h]
    [fulcro.inspect.ui.core :as ui]
    [fulcro.inspect.ui.data-watcher :as dw]
    [fulcro.util :refer [ident?]]
    [taoensso.encore :as enc]))

(defn re-pattern-insensitive [pattern]
  (re-pattern (str "(?i)" pattern)))

(defn highlight-string [s highlight-subs]
  (str/replace s (re-pattern-insensitive highlight-subs)
    (fn [x] (str "<span class=\"highlight\">" x "</span>"))))

(defn highlighter [value highlight]
  (dom/pre {:dangerouslySetInnerHTML
            #js {:__html (highlight-string (str value) highlight)}}))

(defn pprint-highlighter [value highlight]
  (highlighter (with-out-str (pprint value)) highlight))

(defmutation set-current-state [history-step]
  (action [env]
    (h/swap-entity! env assoc :current-state history-step)))

(defn set-path!* [env path]
  (h/swap-entity! env assoc :ui/path {:path path})
  (h/swap-entity! env update :ui/history conj {:path path}))

(defmutation set-path [{:keys [path]}]
  (action [env]
    (set-path!* env path)))

(defn set-path! [this path]
  (let [{::keys [id]} (fc/props this)
        reconciler (fc/any->reconciler this)]
    (fc/transact! reconciler [::id id]
      `[(set-path {:path ~path})])))

(defmutation append-to-path [{:keys [sub-path]}]
  (action [{:as env :keys [state ref]}]
    (h/swap-entity! env update-in [:ui/path :path] into sub-path)
    (h/swap-entity! env update :ui/history conj
      {:path (get-in @state (conj ref :ui/path :path))})))

(defn append-to-path! [this & sub-path]
  (let [{::keys [id]} (fc/props this)
        reconciler (fc/any->reconciler this)]
    (fc/transact! reconciler [::id id]
      `[(append-to-path {:sub-path ~sub-path})])))

(defn settings-env [this]
  (-> this fc/props :fulcro.inspect/settings))

(defn table? [v]
  (and
    (map? v)
    (every? map? (vals v))))

(defn compact [{:setting/keys [compact-keywords?]} s]
  (if compact-keywords?
    (str/join "."
      (let [segments (str/split s #"\.")]
        (conj
          (mapv first
            (drop-last segments))
          (last segments))))
    s))

(defn compact-keyword [env kw]
  (if (and (keyword? kw) (namespace kw))
    (keyword
      (compact env (namespace kw))
      (name kw))
    kw))

(defn ui-ident
  ([env v] (ui-ident env v ""))
  ([{::keys [select-ident] :as env} v hl]
   (let [ident (mapv (partial compact-keyword env) v)]
     (div {:key (str "ident-" v)}
       (a {:href    "#"
           :title   (str v)
           :onClick #(select-ident v)}
         (highlighter (pr-str ident) hl))))))

(defn ui-db-key [env x]
  (cond
    (keyword? x)
    (span {:style {:whiteSpace "nowrap"}} ":"
      (when (namespace x)
        (span {:title (namespace x) :style {:color "grey"}}
          (str (compact env (namespace x)) "/")))
      (span {:style {:fontWeight "bold"}}
        (name x)))

    (ident? x) (ui-ident env x)

    :else (span {:style {:whiteSpace "nowrap"}} (pr-str x))))

(defn ui-db-value [{::keys [select-map] :as env} v k]
  (ui/code {}
    (cond
      (nil? v)
      "nil"

      (keyword? v)
      (ui-db-key env v)

      (map? v)
      (a {:href "#" :onClick #(select-map k)} (str (count (keys v)) " items"))

      (ident? v)
      (ui-ident env v)

      (and (vector? v) (every? ident? v))
      (fc/fragment (map (partial ui-ident env) v))

      :else (pr-str v))))

(defn ui-entity-level [this]
  (let [{:keys [current-state] :ui/keys [path]} (fc/props this)
        {current-state :value} (hist/closest-populated-history-step this (:id current-state))
        entity       (get-in current-state (:path path))
        select-map   (fn [k] (append-to-path! this k))
        select-ident (fn [ident] (set-path! this ident))
        env          (assoc (settings-env this)
                       ::select-ident select-ident
                       ::select-map select-map)]
    (ui/table {}
      (ui/thead {}
        (ui/tr {}
          (ui/th {} "Key")
          (ui/th {} "Value")))

      (if (map? entity)
        (ui/tbody
          (for [[k v] (sort-by (comp str key) entity)]
            (ui/tr {:react-key (str "entity-key-" k)}
              (ui/td (ui-db-key env k))
              (ui/td (ui-db-value env v k)))))))))

(defn ui-table-level [this]
  (let [{:keys [current-state] :ui/keys [path]} (fc/props this)
        {current-state :value} (hist/closest-populated-history-step this (:id current-state))
        entity-ids   (keys (get-in current-state (:path path)))
        select-ident (fn [ident] (set-path! this ident))
        env          (assoc (settings-env this)
                       ::select-ident select-ident)]
    (ui/table {}
      (ui/thead {}
        (ui/tr {}
          (ui/th {} "Entity ID")))
      (ui/tbody
        (for [entity-id (sort-by str entity-ids)]
          (ui/tr {:react-key (str "table-key-" entity-id)}
            (ui/td {}
              (a {:href "#" :onClick #(append-to-path! this entity-id)}
                (ui-db-key env entity-id)))))))))

(defn ui-top-level [this]
  (let [{:keys [current-state]} (fc/props this)
        {current-state :value} (hist/closest-populated-history-step this (:id current-state))
        top-keys       (set (keys current-state))
        tables         (filter
                         (fn [k]
                           (let [v (get current-state k)]
                             (table? v)))
                         top-keys)
        root-values    (select-keys current-state (set/difference top-keys (set tables)))

        tables         (select-keys current-state tables)
        select-ident   (fn [ident] (set-path! this ident))
        select-map     (fn [k] (append-to-path! this k))
        select-top-key (fn [k] (set-path! this [k]))
        env            (assoc (settings-env this)
                         ::select-ident select-ident
                         ::select-map select-map)]
    (ui/table {}
      (ui/thead {}
        (ui/tr {}
          (ui/th {:colSpan "2"}
            (dom/h2 :$margin-micro "Tables")))
        (ui/tr {}
          (ui/th "Table")
          (ui/th "Entities")))

      (ui/tbody {}
        (for [[k v] (sort-by (comp str key) tables)]
          (ui/tr {:react-key (str "top-tables-key-" k)}
            (ui/td {} (ui-db-key env k))
            (ui/td {}
              (a {:href "#" :onClick #(select-top-key k)}
                (str (count (keys v)) " items"))))))

      (ui/thead {}
        (ui/tr {}
          (ui/th {:colSpan "2"}
            (dom/h2 :$margin-micro "Top-Level Keys")))

        (ui/tr {}
          (ui/th {} "Key")
          (ui/th {} "Value")))

      (ui/tbody {}
        (for [[k v] (sort-by (comp str key) root-values)]
          (ui/tr {:react-key (str "top-values-key-" k)}
            (ui/td {} (ui-db-key env k))
            (ui/td {} (ui-db-value env v k))))))))

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

(defn search-for!* [{:as env :keys [reconciler state ref]} {:keys [search-query search-type]}]
  (let [props                  (get-in @state ref)
        current-state          (:current-state props)
        {current-state :value} (hist/closest-populated-history-step reconciler (:id current-state))
        internal-fulcro-tables #{:com.fulcrologic.fulcro.components/queries}
        searchable-state       (reduce dissoc current-state internal-fulcro-tables)]
    (h/swap-entity! env assoc :ui/search-results
      (case search-type
        :search/by-value
        (paths-to-values
          (re-pattern-insensitive search-query)
          searchable-state [])
        :search/by-id
        (paths-to-ids
          (re-pattern-insensitive search-query)
          searchable-state)))))

(defmutation search-for [search-params]
  (action [env]
    (h/swap-entity! env update :ui/history conj search-params)
    (h/swap-entity! env assoc :ui/path search-params)
    (search-for!* env search-params)))

(defn search-for! [this search-query [shift-key? search-type]]
  (let [{::keys [id]} (fc/props this)
        reconciler         (fc/any->reconciler this)
        invert-search-type {:search/by-id    :search/by-value
                            :search/by-value :search/by-id}]
    (fc/transact! reconciler [::id id]
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
  (let [{::keys [id]} (fc/props this)
        reconciler (fc/any->reconciler this)]
    (fc/transact! reconciler [::id id]
      `[(pop-history {})])))

(defn add-data-watch! [this path]
  (let [{::keys [id]} (fc/props this)
        reconciler (fc/any->reconciler this)]
    (fc/transact! reconciler [::id id]
      `[(dw/add-data-watch ~{:path path})])
    (let [[_ app-uuid] id]
      (fc/transact! reconciler
        [:fulcro.inspect.ui.inspector/id app-uuid]
        `[(fm/set-props
            ~{:fulcro.inspect.ui.inspector/tab
              :fulcro.inspect.ui.inspector/page-db})]))))

(defn mode [{:as props :keys [current-state]}]
  (let [{:keys [path search-query]} (:ui/path props)]
    (cond
      search-query :search
      (empty? path) :top
      (and (= 1 (count path))
        (table? (get-in current-state path))) :table
      :else :entity)))

(defn ui-db-path [this]
  (let [{:ui/keys [path] :as props} (fc/props this)
        {:keys [path search-query]} path
        env (settings-env this)]
    (dom/div :$margin-small
      (ui/breadcrumb {}
        (ui/breadcrumb-item {:onClick #(set-path! this [])} "Top")

        (when (seq (drop-last path))
          (map
            (fn [sub-path]
              (fc/fragment {:key (str "db-path-" sub-path)}
                (ui/breadcrumb-separator)
                (ui/breadcrumb-item {:onClick #(set-path! this sub-path)
                                     :title   (str (last sub-path))}
                  (str (compact-keyword env (last sub-path))))))
            (let [[x & xs] (drop-last path)]
              (reductions conj [x] xs))))
        (when (last path)
          (fc/fragment
            (ui/breadcrumb-separator)
            (ui/breadcrumb-item {:disabled (not search-query)
                                 :title    (str (last path))
                                 :onClick  #(set-path! this path)}
              (str (compact-keyword env (last path))))))
        (when search-query
          (fc/fragment
            (ui/breadcrumb-separator)
            (ui/breadcrumb-item {:disabled true}
              (str "\"" search-query "\""))))

        (dom/div :$flex)

        (when (= :entity (mode props))
          (ui/button {:onClick #(add-data-watch! this path)
                      :classes [:.primary :$margin-left-small]}
            "Add to DB Watches"))))))

(defn ui-search-results [this]
  (let [{:ui/keys [search-results path]} (fc/props this)
        {:keys [search-query]} path
        env (assoc (settings-env this)
              ::select-ident #(set-path! this %))]
    (ui/table {}
      (ui/thead {}
        (ui/tr {}
          (ui/th {} "Path")
          (when (some :value search-results)
            (ui/th {} "Value"))))
      (ui/tbody
        ;;TODO: render with folding
        (for [{:keys [path value]} (sort-by (comp str :path) search-results)]
          (ui/tr {:react-key (str "search-path-" path)}
            (ui/td {}
              (ui-ident env path search-query))
            (when value
              (ui/td {}
                (pprint-highlighter value search-query)))))))))

(defn ui-toolbar [this]
  (let [{:ui/keys [history search-query search-type]} (fc/props this)]
    (ui/toolbar {:classes [:.details]}
      (ui/toolbar-action {:onClick  #(pop-history! this)
                          :disabled (empty? history)}
        (ui/icon {:title "Go Back"} :arrow_back))
      (ui/input {:value       search-query
                 :placeholder "Search DB for:"
                 :onChange    #(fm/set-string! this :ui/search-query :event %)
                 :onKeyDown   #(when (= 13 (.-keyCode %))
                                 (search-for! this search-query
                                   [(.-shiftKey %) search-type]))
                 :classes     [:$flex]
                 :style       {:paddingRight "20px"}})
      (ui/toolbar-action {:onClick  #(pop-history! this)
                          :disabled (empty? history)
                          :style    {:marginLeft  "-29px"
                                     :marginRight "7px"
                                     :position    "relative"}}
        (ui/icon {:title "Go Back"} :search))
      (ui/toggler
        {:classes [(if (= search-type :search/by-value) :.active)]
         :onClick #(fm/set-string! this :ui/search-type
                     :value :search/by-value)}
        "by Value")
      (ui/toggler
        {:classes [(if (= search-type :search/by-id) :.active)]
         :onClick #(fm/set-string! this :ui/search-type
                     :value :search/by-id)}
        "by ID"))))

(defn ui-current-mode [this]
  (let [explorer-mode (mode (fc/props this))]
    (case explorer-mode
      :top (ui-top-level this)
      :search (ui-search-results this)
      :table (ui-table-level this)
      :entity (ui-entity-level this)
      (dom/div (str "Unrecognized mode " (pr-str explorer-mode))))))

(defsc DBExplorer [this _]
  {:ident         [::id ::id]
   :query         [:ui/path :ui/history :ui/search-query :ui/search-results :ui/search-type
                   ::id :current-state
                   {[:fulcro.inspect/settings '_] [:setting/compact-keywords?]}]
   :initial-state {:current-state     {}
                   :ui/search-query   ""
                   :ui/search-results []
                   :ui/search-type    :search/by-value
                   :ui/path           {:path []}
                   :ui/history        []}
   :css           [[:.container {:display        "flex"
                                 :flex           "1"
                                 :flex-direction "column"
                                 :max-width      "100%"}]
                   [:.content-container {:max-width "100%"
                                         :flex      "1"
                                         :overflow  "auto"}]]}
  (try
    (dom/div :.container
      (ui-toolbar this)
      (ui-db-path this)
      (dom/div :.content-container
        (ui-current-mode this)))
    (catch :default e
      (dom/div (str "Inspect rendering threw an exception. Start a new tab to try again. Report an issue. Exception:"
                 (ex-message e))))))

(def ui-db-explorer (fc/factory DBExplorer))
