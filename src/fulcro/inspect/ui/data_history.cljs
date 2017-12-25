(ns fulcro.inspect.ui.data-history
  (:require [fulcro.client.primitives :as fp]
            [fulcro.client.dom :as dom]
            [fulcro.client.mutations :as mutations]
            [fulcro.inspect.ui.data-watcher :as watcher]
            [fulcro.inspect.ui.core :as ui]
            [fulcro.inspect.ui.dom-history-viewer :as domv]
            [fulcro.inspect.helpers :as h]))

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
    (let [state-atom (some-> app (:reconciler) (fp/app-state))]
      (reset! state-atom target-state)
      (h/swap-entity! env assoc ::current-index (-> (get-in @state ref) ::history count dec))))
  (refresh [_] [::current-index]))

(fp/defsc DataHistory
  [this {::keys [history watcher current-index show-dom-preview?]} {:keys [target-app]} css]
  {:initial-state (fn [content]
                    {::history-id        (random-uuid)
                     ::history           [(new-state content)]
                     ::current-index     0
                     ::show-dom-preview? true
                     ::watcher           (fp/get-initial-state watcher/DataWatcher content)})
   :ident         [::history-id ::history-id]
   :query         [::history-id ::history ::current-index ::show-dom-preview?
                   {::watcher (fp/get-query watcher/DataWatcher)}]
   :css           [[:.container {:width          "100%"
                                 :flex           "1"
                                 :display        "flex"
                                 :flex-direction "column"}]
                   [:.slider {:display "flex"}]
                   [:.watcher {:flex     "1"
                               :overflow "auto"
                               :padding  "10px"}]
                   [:.toolbar {:padding-left "4px"}]]
   :css-include   [ui/CSS watcher/DataWatcher domv/DOMHistoryView]}
  (let [at-end?   (= (dec (count history)) current-index)
        app-state (-> watcher ::watcher/root-data :fulcro.inspect.ui.data-viewer/content)]
    (dom/div #js {:className (:container css)}
      (ui/toolbar {:className (:toolbar css)}
        (ui/toolbar-action {}
          (dom/input #js {:title    "Show DOM preview."
                          :checked  show-dom-preview?
                          :onChange #(mutations/toggle! this ::show-dom-preview?)
                          :type     "checkbox"}))

        (ui/toolbar-action {:title    "Back one version"
                            :disabled (= 0 current-index)
                            :onClick  #(fp/transact! this (cond-> `[(navigate-history ~{::current-index (dec current-index)})]
                                                            (-> this fp/props ::show-dom-preview?) (conj `(domv/show-dom-preview {}))))}
          (ui/icon :chevron_left))

        (dom/input #js {:type      "range" :min "0" :max (dec (count history))
                        :value     (str current-index)
                        :onMouseUp (fn [] (fp/transact! this `[(domv/hide-dom-preview {})]))
                        :onChange  #(fp/transact! this (cond-> `[(navigate-history {::current-index ~(js/parseInt (.. % -target -value))})]
                                                         (-> this fp/props ::show-dom-preview?) (conj `(domv/show-dom-preview {}))))})

        (ui/toolbar-action {:title    "Foward one version"
                            :disabled at-end?
                            :onClick  #(fp/transact! this (cond-> `[(navigate-history ~{::current-index (inc current-index)})]
                                                            (-> this fp/props ::show-dom-preview?) (conj `(domv/show-dom-preview {}))))}
          (ui/icon :chevron_right))

        (ui/toolbar-action {:title    "Reset App To This State"
                            :disabled at-end?
                            :onClick  #(fp/transact! this `[(reset-app ~{:app target-app :target-state app-state})])}
          (ui/icon :settings_backup_restore)))
      (dom/div #js {:className (:watcher css)}
        (watcher/data-watcher watcher)))))

(def data-history (fp/factory DataHistory))
