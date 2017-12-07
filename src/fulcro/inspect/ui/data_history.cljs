(ns fulcro.inspect.ui.data-history
  (:require [fulcro.client.primitives :as fp]
            [fulcro-css.css :as css]
            [fulcro.client.dom :as dom]
            [fulcro.client.mutations :as mutations]
            [fulcro.inspect.ui.data-watcher :as watcher]
            [fulcro.inspect.ui.core :as ui]
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
  (action [env]
    (let [{:keys [state ref]} env
          history (get-in @state ref)]
      (when (not= current-index (::current-index history))
        (let [content (get-in history [::history current-index ::state])]
          (h/swap-entity! env assoc ::current-index current-index)
          (watcher/update-state (assoc env :ref (::watcher history)) content))))))

(fp/defsc DataHistory
  [this {::keys [history watcher current-index]} _]
  {:initial-state (fn [content]
                    {::history-id    (random-uuid)
                     ::history       [(new-state content)]
                     ::current-index 0
                     ::watcher       (fp/get-initial-state watcher/DataWatcher content)})
   :ident         [::history-id ::history-id]
   :query         [::history-id ::history ::current-index
                   {::watcher (fp/get-query watcher/DataWatcher)}]
   :css           [[:.container {:width          "100%"
                                 :flex           "1"
                                 :display        "flex"
                                 :flex-direction "column"}]
                   [:.slider {:display "flex"}]
                   [:.watcher {:flex "1"
                               :overflow "auto"
                               :padding "10px"}]]
   :css-include   [ui/CSS watcher/DataWatcher]}
  (let [css (css/get-classnames DataHistory)]
    (dom/div #js {:className (:container css)}
      (ui/toolbar {}
        (ui/toolbar-action {:title   "Back one version"
                            :disabled (= 0 current-index)
                            :onClick #(fp/transact! this [`(navigate-history ~{::current-index (dec current-index)})])}
          (ui/icon :chevron_left))

        (dom/input #js {:type     "range" :min "0" :max (dec (count history))
                        :value    (str current-index)
                        :onChange #(fp/transact! this [`(navigate-history {::current-index ~(js/parseInt (.. % -target -value))})])})

        (ui/toolbar-action {:title   "Foward one version"
                            :disabled (= (dec (count history)) current-index)
                            :onClick #(fp/transact! this [`(navigate-history ~{::current-index (inc current-index)})])}
          (ui/icon :chevron_right)))
      (dom/div #js {:className (:watcher css)}
        (watcher/data-watcher watcher)))))

(def data-history (fp/factory DataHistory))
