(ns fulcro.inspect.ui.settings
  (:require
    [fulcro.client.data-fetch :as df]
    [fulcro.client.localized-dom :as dom]
    [fulcro.client.mutations :as m :refer [defmutation]]
    [fulcro.client.primitives :as fp :refer [defsc]]
    [fulcro.inspect.helpers :as h]
    [fulcro.inspect.ui.core :as ui]))

(def settings (atom {}))
(defn get-setting [k]
  (get @settings k))

(defmutation cache-settings [params]
  (action [{:keys [state ref]}]
    (swap! settings merge (get-in @state ref) params)))

(defmutation save-settings [params]
  (remote [env]
    (-> env :ast (assoc :key 'save-settings)))
  (action [env]
    (h/swap-entity! env merge params)
    (swap! settings merge params)))

(defn load-settings-mutation [ident query]
  (df/load-mutation
    {:target        ident
     :query         [{:fulcro.inspect/settings (filterv #(= "setting" (namespace %)) query)}]
     :refresh       [::id]
     :post-mutation `cache-settings}))

(defsc Settings [this {:setting/keys [websocket-port compact-keywords?]} {:keys [close-settings!]}]
  {:query             [::id :setting/websocket-port :setting/compact-keywords?]
   :ident             (fn [] [::id :main])
   :css               [[:.container {:padding "12px"}]]
   :componentDidMount (fn []
                        (fp/transact! this
                          (load-settings-mutation
                            (fp/get-ident this)
                            (fp/get-query this))))
   :initial-state     {::id                       :main
                       :setting/websocket-port    0
                       :setting/compact-keywords? true}}
  (dom/div
    (when close-settings!
      (ui/toolbar {:classes [:.details]}
        (ui/toolbar-spacer)
        (ui/toolbar-action {:onClick close-settings!}
          (ui/icon {:title "Close panel"} :clear))))
    (dom/div :.container
      (ui/header {} "Settings")
      (dom/div :$margin-left-standard
        (ui/row {:classes [:.align-center]}
          (ui/label "Compact Keywords in DB Explorer:")
          (dom/input :.ui.checkbox
            {:checked  (or compact-keywords? false)
             :type     "checkbox"
             :onChange #(fp/transact! this `[(save-settings {:setting/compact-keywords? ~(not compact-keywords?)})])}))
        (ui/row {:classes [:.align-center]}
          (ui/label "Websocket Port:")
          (ui/input {:value    (or websocket-port 0)
                     :type     "number"
                     :onChange #(m/set-integer! this :setting/websocket-port :event %)})
          (ui/button {:onClick #(fp/transact! this `[(save-settings {:setting/websocket-port ~websocket-port})])
                      :style   {:alignSelf "center"}}
            "Restart Websockets"))))))

(def ui-settings (fp/factory Settings))
