(ns fulcro.inspect.ui.data-history
  (:require
    [cljs.pprint :refer [pprint]]
    [com.fulcrologic.devtools.devtool-io :as devtool]
    [com.fulcrologic.fulcro-css.localized-dom :as dom]
    [com.fulcrologic.fulcro.algorithms.normalized-state :as fns]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.dom.events :as evt]
    [com.fulcrologic.fulcro.mutations :as m]
    [fulcro.inspect.helpers :as db.h]
    [fulcro.inspect.lib.history :as hist]
    [fulcro.inspect.ui.core :as ui]
    [fulcro.inspect.ui.data-viewer :as data-viewer]
    [fulcro.inspect.ui.data-watcher :as watcher]
    [fulcro.inspect.ui.events :as events]
    [garden.selectors :as gs]))

(def ^:dynamic *max-history* 80)

(m/defmutation set-content [history-step]
  (action [env]
    (let [{:keys [state ref]} env
          {:keys [:data-history/watcher :data-history/current-index :data-history/history]} (get-in @state ref)]
      (if (or (= 0 (count history))
            (= current-index (dec (count history))))
        (when-not (= current-index (dec *max-history*))
          (db.h/swap-entity! env update :data-history/current-index inc))

        (if (= *max-history* (count history))
          (db.h/swap-entity! env update :data-history/current-index dec)))

      (db.h/swap-entity! env update :data-history/history #(->> (conj % history-step)
                                                             (take-last *max-history*)
                                                             (vec))))))

(m/defmutation navigate-history [{:keys [:data-history/current-index]}]
  (action [{:keys [state ref] :as env}]
    (db.h/swap-entity! env assoc :data-history/current-index current-index)))

(defn has-state? [{:keys [state ref app] :as env} {:keys [:data-history/current-index]}]
  (let [history (get-in @state ref)]
    (let [state-id         (get-in history [:data-history/history current-index ::state-id])
          app-uuid         (second (get history :data-history/id))
          {::hist/keys [db-hash-index]} (comp/shared app)
          historical-state (get-in @db-hash-index [app-uuid state-id])]
      (not (empty? historical-state)))))

(m/defmutation fetch-and-show-history [{:keys [:data-history/current-index] :as params}]
  (action [{:keys [state ref app] :as env}]
    (let [history (get-in @state ref)]
      (when (not= current-index (:data-history/current-index history))
        (let [{:history/keys [value version]} (fns/get-in-graph @state (into ref [:data-history/history current-index]))]
          (comp/transact! app [(navigate-history {:data-history/current-index current-index})] {:ref ref})
          (when-not value (hist/fetch-history-step! app version)))))))

(m/defmutation hide-dom-preview [_]
  (remote [env]
    (db.h/remote-mutation env 'hide-dom-preview)))

(m/defmutation reset-app [params]
  (action [{:keys [state ref] :as env}]
    (db.h/swap-entity! env assoc :data-history/current-index (-> (get-in @state ref) :data-history/history count dec)))
  (refresh [_] [:data-history/current-index])
  (remote [{:keys [ref] :as env}]
    (-> env
      (m/with-server-side-mutation 'reset-app)
      (m/with-params (merge params {:fulcro.inspect.core/app-uuid (db.h/ref-app-uuid ref)})))))

(comp/defsc DataHistory
  [this {:keys [:data-history/search :data-history/history :data-history/watcher :data-history/current-index]} _ css]
  {:initial-state (fn [{:keys [id] :as params}]
                    {:data-history/id            [:x id]
                     :data-history/search        ""
                     :data-history/history       []
                     :data-history/current-index 0
                     :data-history/watcher       (comp/get-initial-state watcher/DataWatcher params)})
   :ident         :data-history/id
   :query         [:data-history/search :data-history/id
                   {:data-history/history (comp/get-query hist/HistoryStep)} ; to-many
                   :data-history/current-index
                   {:data-history/watcher (comp/get-query watcher/DataWatcher)}]
   :css           [[:.container {:width          "100%"
                                 :flex           "1"
                                 :display        "flex"
                                 :flex-direction "column"}]
                   [:.slider {:display "flex"}]
                   [:.watcher {:flex       "1"
                               :overflow   "auto"
                               :max-height "100%"
                               :padding    "10px"}]
                   [:.toolbar {:padding-left "4px"}]
                   [:.row-content {:display "flex"
                                   :flex    "1"}]
                   [:.snapshots {:border-left "1px solid #a3a3a3"
                                 :width       "220px"
                                 :overflow    "auto"}]
                   [:.snapshots-toggler {:background "#a3a3a3"
                                         :cursor     "pointer"
                                         :width      "1px"}]
                   [(gs/> :.snapshots (gs/div (gs/nth-child "odd"))) {:background "#f5f5f5"}]]
   :css-include   [ui/CSS watcher/DataWatcher]}
  (let [at-end? (= (dec (count history)) current-index)
        {:history/keys [version value]
         :or           {value {}}
         :as           history-step} (get history current-index)]
    (dom/div :.container

      (ui/toolbar {:className (:toolbar css)}
        (ui/toolbar-action {:disabled (= 0 current-index)
                            :onClick  #(comp/transact! this [(fetch-and-show-history {:data-history/current-index (dec current-index)})])}
          (ui/icon {:title "Back one version"} :chevron_left))

        (dom/input {:type     "range" :min "0" :max (dec (count history))
                    :value    (str current-index)
                    :onChange #(comp/transact! this [(fetch-and-show-history {:data-history/current-index (js/parseInt (evt/target-value %))})])})

        (ui/toolbar-action {:disabled at-end?
                            :onClick  #(comp/transact! this [(fetch-and-show-history {:data-history/current-index (inc current-index)})])}
          (ui/icon {:title "Forward one version"} :chevron_right))

        (ui/toolbar-action {:onClick #(comp/transact! this [(reset-app {:history/version version})])}
          (ui/icon {:title (if at-end? "Force app re-render" "Reset App To This State")} :settings_backup_restore))

        (ui/toolbar-debounced-text-field
          {:placeholder "Search (press return to expand tree)"
           :value       (or search "")
           :style       {:margin "0 6px"
                         :width  "210px"}
           :onKeyDown   (fn [e]
                          (when (= (.-keyCode e) (get events/KEYS "return"))
                            (.preventDefault e)
                            (comp/transact! this [(data-viewer/search-expand
                                                    {:viewer (::watcher/root-data watcher)
                                                     :search search})])))
           :onChange    #(m/set-string! this :data-history/search :event %)})
        (dom/div {:className (:flex ui/scss)}))

      (dom/div :.row-content
        (dom/div :.watcher
          (watcher/ui-data-watcher watcher {:history-step history-step
                                            :search       search})))

      (dom/pre
        (with-out-str
          (pprint (dissoc (app/current-state this)
                    :fulcro.inspect.ui.multi-inspector/multi-inspector
                    :fulcro.inspect.ui.db-explorer/id
                    :fulcro.inspect.ui.transactions/tx-list-id
                    :fulcro.inspect.ui.multi-oge/id
                    :fulcro.inspect.ui.inspector/id
                    :oge/id
                    :history/id)))))))

(def data-history (comp/factory DataHistory))
