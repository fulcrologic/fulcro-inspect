(ns fulcro.inspect.ui.settings
  (:require
    [fulcro.client.data-fetch :as df]
    [fulcro.client.localized-dom :as dom]
    [fulcro.client.mutations :as m :refer [defmutation]]
    [fulcro.client.primitives :as fp :refer [defsc]]
    [fulcro.inspect.helpers :as h]
    [fulcro.inspect.ui.core :as ui]))

(defmutation update-settings [params]
  (action [{:keys [state] :as env}]
    (swap! state update :fulcro.inspect/settings merge params)))

(defmutation save-settings [params]
  (remote [env]
    (-> env :ast (assoc :key 'save-settings)))
  (action [{:keys [state] :as env}]
    (swap! state update :fulcro.inspect/settings merge params)))

(defsc SettingsQuery [_ _]
  {:query [:setting/websocket-port :setting/compact-keywords?]})

(defn load-settings [app]
  (df/load app :fulcro.inspect/settings SettingsQuery))

(defsc Settings
  [this
   {:keys [fulcro.inspect/settings]}
   {:keys [close-settings!]}]
  {:query         [::id
                   {[:fulcro.inspect/settings '_]
                    [:setting/websocket-port :setting/compact-keywords?]}]
   :ident         (fn [] [::id :main])
   :css           [[:.container {:padding "12px"}]]
   :initial-state {::id :main}}
  (let [{:setting/keys [websocket-port compact-keywords?]} settings]
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
            (ui/label "Websocket Port:")
            (ui/input {:value    (or websocket-port 0)
                       :type     "number"
                       :onChange #(fp/transact! this `[(update-settings {:setting/websocket-port ~(js/parseInt (m/target-value %))})])})
            (ui/primary-button {:onClick #(fp/transact! this `[(save-settings {:setting/websocket-port ~websocket-port})])}
              "Restart Websockets"))
          (ui/row {:classes [:.align-center]}
            (ui/label
              (dom/input :$margin-right-small
                {:checked  (or compact-keywords? false)
                 :type     "checkbox"
                 :onChange #(fp/transact! this `[(save-settings {:setting/compact-keywords? ~(not compact-keywords?)})])})
              "Compact Keywords in DB Explorer?")))))))

(def ui-settings (fp/factory Settings))
