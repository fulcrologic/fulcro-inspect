(ns fulcro.inspect.ui.settings
  (:require
    [com.fulcrologic.fulcro-css.localized-dom :as dom]
    [com.fulcrologic.fulcro.components :as fp :refer [defsc]]
    [com.fulcrologic.fulcro.mutations :refer [defmutation]]
    [com.fulcrologic.fulcro.data-fetch :as df]
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
   {:keys    [fulcro.inspect/settings]
    :ui/keys [hide-websocket?]}
   {:keys [close-settings!]}]
  {:ident             (fn [] [::id :main])
   :query             [::id :ui/hide-websocket?
                       {[:fulcro.inspect/settings '_]
                        [:setting/websocket-port :setting/compact-keywords?]}]
   :componentDidMount (fn [] (load-settings this))
   :initial-state     {::id :main}
   :css               [[:.container {:padding "12px"}]]}
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
          (if-not hide-websocket?
            (ui/row {:classes [:.align-center]}
              (ui/label "Websocket Port:")
              (ui/input {:value    (or websocket-port 0)
                         :type     "number"
                         :onChange #(fp/transact! this `[(update-settings {:setting/websocket-port ~(js/parseInt (m/target-value %))})])})
              (ui/primary-button {:onClick #(fp/transact! this `[(save-settings {:setting/websocket-port ~websocket-port})])}
                "Restart Websockets")))
          (ui/row {:classes [:.align-center]}
            (ui/label
              (dom/input :$margin-right-small
                {:checked  (or compact-keywords? false)
                 :type     "checkbox"
                 :onChange #(fp/transact! this `[(save-settings {:setting/compact-keywords? ~(not compact-keywords?)})])})
              "Compact Keywords in DB Explorer?")))))))

(def ui-settings (fp/factory Settings))
