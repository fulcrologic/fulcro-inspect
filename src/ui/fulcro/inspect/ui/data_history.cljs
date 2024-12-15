(ns fulcro.inspect.ui.data-history
  (:require
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
          {::keys [watcher current-index history]} (get-in @state ref)]
      (if (or (= 0 (count history))
            (= current-index (dec (count history))))
        (when-not (= current-index (dec *max-history*))
          (db.h/swap-entity! env update ::current-index inc))

        (if (= *max-history* (count history))
          (db.h/swap-entity! env update ::current-index dec)))

      (db.h/swap-entity! env update ::history #(->> (conj % history-step)
                                                 (take-last *max-history*)
                                                 (vec))))))

(m/defmutation navigate-history [{::keys [current-index]}]
  (action [{:keys [state ref] :as env}]
    (db.h/swap-entity! env assoc ::current-index current-index)))

(defn has-state? [{:keys [state ref app] :as env} {::keys [current-index]}]
  (let [history (get-in @state ref)]
    (let [state-id         (get-in history [::history current-index ::state-id])
          app-uuid         (second (get history ::history-id))
          {::hist/keys [db-hash-index]} (comp/shared app)
          historical-state (get-in @db-hash-index [app-uuid state-id])]
      (not (empty? historical-state)))))

(m/defmutation fetch-and-show-history [{::keys [current-index] :as params}]
  (action [{:keys [state ref app] :as env}]
    (let [history (get-in @state ref)]
      (when (not= current-index (::current-index history))
        (let [{:history/keys [value version]} (fns/get-in-graph @state (into ref [::history current-index]))]
          (comp/transact! app [(navigate-history {::current-index current-index})] {:ref ref})
          (when-not value (hist/fetch-history-step! app version)))))))

(m/defmutation hide-dom-preview [_]
  (remote [env]
    (db.h/remote-mutation env 'hide-dom-preview)))

(m/defmutation reset-app [params]
  (action [{:keys [state ref] :as env}]
    (db.h/swap-entity! env assoc ::current-index (-> (get-in @state ref) ::history count dec)))
  (refresh [_] [::current-index])
  (remote [{:keys [ref] :as env}]
    (-> env
      (m/with-server-side-mutation 'reset-app)
      (m/with-params (merge params {:fulcro.inspect.core/app-uuid (db.h/ref-app-uuid ref)})))))

(comp/defsc DataHistory
  [this {::keys [search history watcher current-index]} _ css]
  {:initial-state (fn [content]
                    {::history-id    (random-uuid)
                     ::history       [{:history/version 0
                                       ::app/id         nil
                                       :history/value   {}}]
                     ::search        ""
                     ::current-index 0
                     ::watcher       (comp/get-initial-state watcher/DataWatcher (dissoc content :fulcro.inspect.client/state-hash))})
   :ident         ::history-id
   :query         [::search ::history-id
                   {::history (comp/get-query hist/HistoryStep)} ; to-many
                   ::current-index ::show-dom-preview? ::show-snapshots?
                   {::watcher (comp/get-query watcher/DataWatcher)}]
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
         :as history-step} (get history current-index)]
    (dom/div :.container
      (ui/toolbar {:className (:toolbar css)}
        (ui/toolbar-action {:disabled (= 0 current-index)
                            :onClick  #(comp/transact! this [(fetch-and-show-history {::current-index (dec current-index)})])}
          (ui/icon {:title "Back one version"} :chevron_left))

        (dom/input {:type     "range" :min "0" :max (dec (count history))
                    :value    (str current-index)
                    :onChange #(comp/transact! this [(fetch-and-show-history {::current-index (js/parseInt (evt/target-value %))})])})

        (ui/toolbar-action {:disabled at-end?
                            :onClick  #(comp/transact! this [(fetch-and-show-history {::current-index (inc current-index)})])}
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
           :onChange    #(m/set-string! this ::search :event %)})
        (dom/div {:className (:flex ui/scss)}))

      (dom/div :.row-content
        (dom/div :.watcher
          (watcher/ui-data-watcher watcher {:history-step history-step
                                         :search          search}))))))

(def data-history (comp/factory DataHistory))
