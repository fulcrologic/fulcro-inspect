(ns fulcro.inspect.ui.data-history
  (:require [fulcro.client.primitives :as fp]
            [fulcro.client.localized-dom :as dom]
            [fulcro.client.mutations :as fm]
            [garden.selectors :as gs]
            [fulcro.inspect.lib.local-storage :as storage]
            [fulcro.inspect.ui.data-viewer :as data-viewer]
            [fulcro.inspect.ui.data-watcher :as watcher]
            [fulcro.inspect.ui.core :as ui]
            [fulcro.inspect.ui.dom-history-viewer :as domv]
            [fulcro.inspect.helpers :as h]
            [fulcro.inspect.ui.helpers :as ui.h]
            [fulcro.inspect.helpers :as db.h]))

(def ^:dynamic *max-history* 100)

(defn new-state [content]
  {::state     content
   ::timestamp (js/Date.)})

(fm/defmutation ^:intern set-content [content]
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

(fm/defmutation ^:intern navigate-history [{::keys [current-index]}]
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

(fm/defmutation reset-app [{:keys [app target-state]}]
  (action [{:keys [state ref] :as env}]
    (let [reconciler (some-> app :reconciler)
          state-atom (some-> reconciler fp/app-state)]
      (reset! state-atom target-state)
      (h/swap-entity! env assoc ::current-index (-> (get-in @state ref) ::history count dec))
      (js/setTimeout #(fp/force-root-render! reconciler) 10)))
  (refresh [_] [::current-index]))

(fp/defsc Snapshot
  [this
   {::keys   [snapshot-date snapshot-label]
    :ui/keys [label-editor]
    :as      props}
   {::keys [on-pick-snapshot on-delete-snapshot current?
            on-update-snapshot]}
   css]
  {:initial-state (fn [data] {::snapshot-id    (random-uuid)
                              ::snapshot-db    data
                              ::snapshot-label "New Snapshot"
                              ::snapshot-date  (js/Date.)
                              :ui/label-editor (fp/get-initial-state ui/InlineEditor {})})
   :ident         [::snapshot-id ::snapshot-id]
   :query         [::snapshot-id ::snapshot-db ::snapshot-label ::snapshot-date
                   {:ui/label-editor (fp/get-query ui/InlineEditor)}]
   :css           [[:.container {:display     "flex"
                                 :align-items "center"}
                    [:&:hover [:.action {:visibility "visible"}]]]
                   [:.current {:background "#3c7bd6 !important"}
                    [(ui/foreign-class ui/InlineEditor :label)
                     (ui/foreign-class ui/InlineEditor :no-label)
                     {:color "#fff"}]
                    [:.action {:fill "#fff"}]]
                   [:.action {:cursor     "pointer"
                              :visibility "hidden"
                              :fill       ui/color-text-normal
                              :transform  "scale(0.8)"}]
                   [:.label {:display     "flex"
                             :flex        "1"
                             :padding     "0 6px"
                             :font-family ui/label-font-family
                             :font-size   ui/label-font-size}]
                   [:.date (merge ui/css-timestamp {:margin "0"})]
                   [:.flex {:flex "1"}]]}
  (dom/div :.container {:className (if current? (:current css))}
    (dom/div :.label
      (ui/inline-editor label-editor
        {::ui/value     snapshot-label
         ::ui/on-change (fn [new-label] (on-update-snapshot (assoc props ::snapshot-label new-label)))}))
    (dom/div :.action.pick {:onClick #(on-pick-snapshot props)}
      (ui/icon {:title "Restore snapshot"} :settings_backup_restore))
    (dom/div :.action.remove {:onClick #(on-delete-snapshot props)}
      (ui/icon {:title "Delete snapshot"} :delete_forever))))

(def snapshot (ui.h/computed-factory Snapshot {:keyfn ::snapshot-id}))

(fm/defmutation save-snapshot [{::keys [snapshot-db]}]
  (action [{:keys [ref state component] :as env}]
    (let [ss        (h/create-entity! env Snapshot snapshot-db)
          new-ident (fp/get-ident Snapshot ss)
          app-id    (db.h/ref-app-id @state ref)]
      (swap! state
        #(db.h/update-matching-apps % app-id
           (fn [s app-uuid]
             (update-in s [:fulcro.inspect.ui.data-history/history-id
                           [:fulcro.inspect.core/app-uuid app-uuid]
                           ::snapshots]
               conj new-ident))))

      (let [snapshots (-> (h/query-component component) ::snapshots)]
        (storage/tset! [::snapshots app-id] snapshots)))

    (h/swap-entity! env assoc ::show-snapshots? true)))

(fm/defmutation update-snapshot-label [{::keys [snapshot-id snapshot-label]}]
  (action [{:keys [ref component] :as env}]
    (h/swap-entity! (assoc env :ref [::snapshot-id snapshot-id]) assoc ::snapshot-label snapshot-label)

    (let [snapshots (-> (h/query-component component) ::snapshots)]
      (storage/tset! [::snapshots (db.h/ref-app-uuid ref)] snapshots))))

(fm/defmutation delete-snapshot [{::keys [snapshot-id]}]
  (action [{:keys [ref state component] :as env}]
    (let [sref   [::snapshot-id snapshot-id]
          app-id (db.h/ref-app-id @state ref)]
      (swap! state
        (comp
          #(h/deep-remove-ref % sref)
          #(db.h/update-matching-apps % app-id
             (fn [s app-uuid]
               (update-in s [:fulcro.inspect.ui.data-history/history-id
                             [:fulcro.inspect.core/app-uuid app-uuid]
                             ::snapshots]
                 (fn [ss] (vec (remove #{sref} ss))))))))

      (let [snapshots (-> (h/query-component component) ::snapshots)]
        (storage/tset! [::snapshots app-id] snapshots)))))

(fp/defsc DataHistory
  [this {::keys [history watcher current-index show-dom-preview? show-snapshots? snapshots]} {:keys [target-app]} css]
  {:initial-state (fn [content]
                    {::history-id        (random-uuid)
                     ::history           [(new-state content)]
                     ::current-index     0
                     ::show-dom-preview? true
                     ::show-snapshots?   true
                     ::watcher           (fp/get-initial-state watcher/DataWatcher content)
                     ::snapshots         []})
   :ident         [::history-id ::history-id]
   :query         [::history-id ::history ::current-index ::show-dom-preview? ::show-snapshots?
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
                   [:.snapshots {:border-left "1px solid #a3a3a3"
                                 :width       "220px"
                                 :overflow    "auto"}]
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
                      :onChange #(fm/toggle! this ::show-dom-preview?)
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
          (ui/icon {:title "Save snapshot of current state"} :add_a_photo))
        (dom/div {:className (:flex ui/scss)})
        (ui/toolbar-action {:disabled (not (seq snapshots))}
          (ui/icon {:onClick #(fm/toggle! this ::show-snapshots?)
                    :title   (if (seq snapshots) "Toggle snapshots view." "Record a snapshot to enable the snapshots view.")}
            :wallpaper)))

      (dom/div :.row-content
        (dom/div :.watcher
          (watcher/data-watcher watcher))

        (if (and show-snapshots? (seq snapshots))
          (dom/div :.snapshots
            #_
            (ui/toolbar {}
              (dom/div {:className (:flex ui/scss)})
              (ui/toolbar-action {:disabled true}
                (ui/icon {:title "Not implemented yet."} :file_upload))
              (ui/toolbar-action {:disabled true}
                (ui/icon {:title "Not implemented yet."} :file_download)))
            (for [s (sort-by ::snapshot-label #(compare %2 %) snapshots)]
              (snapshot s {::current?           (= (get-in watcher [::watcher/root-data ::data-viewer/content])
                                                  (get s ::snapshot-db))
                           ::on-delete-snapshot (fn [{::keys [snapshot-label] :as s}]
                                                  (if (js/confirm (str "Delete " snapshot-label " snapshot?"))
                                                    (fp/transact! this `[(delete-snapshot ~s)])))
                           ::on-pick-snapshot   (fn [{::keys [snapshot-db]}]
                                                  (fp/transact! this `[(reset-app ~{:app target-app :target-state snapshot-db})]))
                           ::on-update-snapshot (fn [snapshot]
                                                  (fp/transact! this `[(update-snapshot-label ~snapshot)]))}))))))))

(def data-history (fp/factory DataHistory))
