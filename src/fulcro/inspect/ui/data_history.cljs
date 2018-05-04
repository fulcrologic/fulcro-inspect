(ns fulcro.inspect.ui.data-history
  (:require [fulcro.client.primitives :as fp]
            [fulcro.client.localized-dom :as dom]
            [fulcro.client.mutations :as mutations]
            [garden.selectors :as gs]
            [fulcro.inspect.lib.local-storage :as storage]
            [fulcro.inspect.ui.data-viewer :as data-viewer]
            [fulcro.inspect.ui.data-watcher :as watcher]
            [fulcro.inspect.ui.core :as ui]
            [fulcro.inspect.ui.dom-history-viewer :as domv]
            [fulcro.inspect.helpers :as h]
            [fulcro.inspect.ui.helpers :as ui.h]))

(def ^:dynamic *max-history* 100)

(defn new-state [content]
  {::state     content
   ::timestamp (js/Date.)})

(mutations/defmutation ^:intern set-content [content]
  (action [env]
    (let [{:keys [state ref]} env
          {::keys [watcher current-index history]} (get-in @state ref)]
      (if (or (= 0 (count history))
              (= current-index (dec (count history))))
        (do
          (if-not (= current-index (dec *max-history*))
            (h/swap-entity! env update ::current-index inc))
          (watcher/update-state (assoc env :ref watcher) content))

        (if (= *max-history* (count history))
          (h/swap-entity! env update ::current-index dec)))

      (h/swap-entity! env update ::history #(->> (conj % (new-state content))
                                                 (take-last *max-history*)
                                                 (vec))))))

(mutations/defmutation ^:intern navigate-history [{::keys [current-index]}]
  (action [{:keys [state ref] :as env}]
    (let [history (get-in @state ref)]
      (when (not= current-index (::current-index history))
        (let [content                 (get-in history [::history current-index ::state])
              history-view-state-path (conj (fp/get-ident domv/DOMHistoryView {}) :app-state)]
          (if (::show-dom-preview? history)
            (swap! state assoc-in history-view-state-path content))
          (h/swap-entity! env assoc ::current-index current-index)
          (watcher/update-state (assoc env :ref (::watcher history)) content)))))
  (refresh [env] [:ui/historical-dom-view]))

(mutations/defmutation reset-app [{:keys [app target-state]}]
  (action [{:keys [state ref] :as env}]
    (let [reconciler (some-> app :reconciler)
          state-atom (some-> reconciler fp/app-state)]
      (reset! state-atom target-state)
      (h/swap-entity! env assoc ::current-index (-> (get-in @state ref) ::history count dec))
      (js/setTimeout #(fp/force-root-render! reconciler) 10)))
  (refresh [_] [::current-index]))

(fp/defsc Snapshot
  [this
   {::keys [snapshot-date snapshot-label] :as props}
   {::keys [on-pick-snapshot on-delete-snapshot current?]}
   css]
  {:initial-state (fn [data] {::snapshot-id    (random-uuid)
                              ::snapshot-db    data
                              ::snapshot-label "New Snapshot"
                              ::snapshot-date  (js/Date.)})
   :ident         [::snapshot-id ::snapshot-id]
   :query         [::snapshot-id ::snapshot-db ::snapshot-label ::snapshot-date]
   :css           [[:.container {:display     "flex"
                                 :align-items "center"}]
                   [:.current {:background "#deeefe !important"}]
                   [:.action {:cursor "pointer"
                              :transform "scale(0.8)"}]
                   [:.label {:font-family ui/label-font-family
                             :font-size   ui/label-font-size}]
                   [:.date (merge ui/css-timestamp {:margin "0"})]
                   [:.flex {:flex "1"}]
                   [:.pick :.remove {:margin "0 3px"}]]}
  (dom/div :.container {:className (if current? (:current css))}
    (dom/div :.action.pick {:onClick #(on-pick-snapshot props)}
      (ui/icon :settings_backup_restore))
    (dom/div :.flex
      (dom/div :.label (str snapshot-label)))
    (dom/div :.action.remove {:onClick #(on-delete-snapshot props)}
      (ui/icon :delete_forever))))

(def snapshot (ui.h/computed-factory Snapshot {:keyfn ::snapshot-id}))

(mutations/defmutation save-snapshot [{::keys [snapshot-db]}]
  (action [{:keys [ref state component] :as env}]
    (let [ss        (h/create-entity! env Snapshot snapshot-db)
          new-ident (fp/get-ident Snapshot ss)
          app-id    (ui.h/ref-app-id ref)
          apps      (ui.h/matching-apps @state app-id)]
      (swap! state (fn [s]
                     (reduce
                       (fn [s app]
                         (update-in s [:fulcro.inspect.ui.data-history/history-id
                                       [:fulcro.inspect.core/app-id app]
                                       ::snapshots]
                           conj new-ident))
                       s
                       apps))))

    (let [snapshots (-> (h/query-component component) ::snapshots)]
      (storage/set! [::snapshots (ui.h/ref-app-id ref)] snapshots))))

(mutations/defmutation delete-snapshot [{::keys [snapshot-id]}]
  (action [{:keys [ref state component] :as env}]
    (let [sref   [::snapshot-id snapshot-id]
          app-id (ui.h/ref-app-id ref)]
      (swap! state
        (comp
          #(h/deep-remove-ref % sref)
          #(ui.h/update-matching-apps % app-id
             (fn [s app]
               (update-in s [:fulcro.inspect.ui.data-history/history-id
                             [:fulcro.inspect.core/app-id app]
                             ::snapshots]
                 (fn [ss] (vec (remove #{sref} ss)))))))))

    (let [snapshots (-> (h/query-component component) ::snapshots)]
      (storage/set! [::snapshots (ui.h/ref-app-id ref)] snapshots))))

(fp/defsc DataHistory
  [this {::keys [history watcher current-index show-dom-preview? snapshots]} {:keys [target-app]} css]
  {:initial-state (fn [content]
                    {::history-id        (random-uuid)
                     ::history           [(new-state content)]
                     ::current-index     0
                     ::show-dom-preview? true
                     ::show-snapshots?   true
                     ::watcher           (fp/get-initial-state watcher/DataWatcher content)
                     ::snapshots         []})
   :ident         [::history-id ::history-id]
   :query         [::history-id ::history ::current-index ::show-dom-preview?
                   {::watcher (fp/get-query watcher/DataWatcher)}
                   {::snapshots (fp/get-query Snapshot)}]
   :css           [[:.container {:width          "100%"
                                 :flex           "1"
                                 :display        "flex"
                                 :flex-direction "column"}]
                   [:.slider {:display "flex"}]
                   [:.watcher {:flex     "1"
                               :overflow "auto"
                               :padding  "10px"}]
                   [:.toolbar {:padding-left "4px"}]
                   [:.row-content {:display "flex"
                                   :flex    "1"}]
                   [:.snapshots {:width "20%"
                                 :overflow "auto"}]
                   [:.snapshots-toggler {:background "#a3a3a3"
                                         :cursor     "pointer"
                                         :width      "1px"}]
                   [(gs/> :.snapshots (gs/div (gs/nth-child "odd"))) {:background "#f5f5f5"}]]
   :css-include   [ui/CSS watcher/DataWatcher domv/DOMHistoryView Snapshot]}
  (let [at-end?   (= (dec (count history)) current-index)
        app-state (-> watcher ::watcher/root-data :fulcro.inspect.ui.data-viewer/content)]
    (dom/div :.container
      (ui/toolbar {:className (:toolbar css)}
        (ui/toolbar-action {}
          (dom/input {:title    "Show DOM preview."
                      :checked  show-dom-preview?
                      :onChange #(mutations/toggle! this ::show-dom-preview?)
                      :type     "checkbox"}))

        (ui/toolbar-action {:disabled (= 0 current-index)
                            :onClick  #(fp/transact! this (cond-> `[(navigate-history ~{::current-index (dec current-index)})]
                                                            (-> this fp/props ::show-dom-preview?) (conj `(domv/show-dom-preview {}))))}
          (ui/icon {:title "Back one version"} :chevron_left))

        (dom/input {:type      "range" :min "0" :max (dec (count history))
                    :value     (str current-index)
                    :onMouseUp (fn [] (fp/transact! this `[(domv/hide-dom-preview {})]))
                    :onChange  #(fp/transact! this (cond-> `[(navigate-history {::current-index ~(js/parseInt (.. % -target -value))})]
                                                     (-> this fp/props ::show-dom-preview?) (conj `(domv/show-dom-preview {}))))})

        (ui/toolbar-action {:disabled at-end?
                            :onClick  #(fp/transact! this (cond-> `[(navigate-history ~{::current-index (inc current-index)})]
                                                            (-> this fp/props ::show-dom-preview?) (conj `(domv/show-dom-preview {}))))}
          (ui/icon {:title "Foward one version"} :chevron_right))

        (ui/toolbar-action {:disabled at-end?
                            :onClick  #(fp/transact! this `[(reset-app ~{:app target-app :target-state app-state})])}
          (ui/icon {:title "Reset App To This State"} :settings_backup_restore))

        (ui/toolbar-action {:onClick #(let [content (-> this fp/get-reconciler fp/app-state deref
                                                        (h/get-in-path [::watcher/id (::watcher/id watcher) ::watcher/root-data ::data-viewer/content]))]
                                        (fp/transact! this [`(save-snapshot {::snapshot-db ~content})]))}
          (ui/icon {:title "Save snapshot of current state"} :add_a_photo)))

      (dom/div :.row-content
        (dom/div :.watcher
          (watcher/data-watcher watcher))

        (dom/div :.snapshots-toggler
          )
        (dom/div :.snapshots
          (for [s (sort-by ::snapshot-date #(compare %2 %) snapshots)]
            (snapshot s {::current?           (= (get-in watcher [::watcher/root-data ::data-viewer/content])
                                                (get s ::snapshot-db))
                         ::on-delete-snapshot (fn [{::keys [snapshot-label] :as s}]
                                                (if (js/confirm (str "Delete " snapshot-label " snapshot?"))
                                                  (fp/transact! this `[(delete-snapshot ~s)])))
                         ::on-pick-snapshot   (fn [{::keys [snapshot-db]}]
                                                (fp/transact! this `[(reset-app ~{:app target-app :target-state snapshot-db})]))})))))))

(def data-history (fp/factory DataHistory))
