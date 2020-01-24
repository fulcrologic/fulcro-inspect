(ns fulcro.inspect.ui.settings
  (:require
    [fulcro.client.dom :as dom :refer [div button input a]]
    [fulcro.client.mutations :as m :refer [defmutation]]
    [fulcro.client.primitives :as fp :refer [defsc]]
    [fulcro.client.data-fetch :as df]))

(defmutation restart-websockets [_]
  (remote [env]
    (-> env :ast (assoc :key 'restart-websockets))))

(defsc Settings [this {:setting/keys [websocket-port] :as props} {:keys [close-settings!]}]
  {:query             [::id :setting/websocket-port]
   :ident             (fn [] [::id "main"])
   :componentDidMount (fn []
                        (fp/transact! this
                          (df/load-mutation {:target  (fp/get-ident this)
                                             :query   [:>/SETTINGS]
                                             :refresh [:setting/websocket-port]})))
   :initial-state     {}}
  (div :.ui.container {:style {:margin "1rem"}}
    (button :.ui.button.labeled.icon.basic.negative
      {:onClick close-settings!}
      (dom/i :.icon.close)
      "Close Settings")
    (div :.ui.divider.horizontal.hidden)
    (div :.ui.form.big
      (div :.fields
        (when (-> this fp/shared :fulcro.inspect.renderer/electron?)
          (fp/fragment
            (div :.field.inline
              (dom/label "Websocket Port: ")
              (input {:value    websocket-port
                      :type     "number"
                      :onChange #(m/set-integer! this :setting/websocket-port :event %)}))
            (button :.ui.button.primary.positive
              {:onClick #(fp/transact! this `[(restart-websockets {:port ~websocket-port})])}
              (dom/span :.ui.text.large "Restart Websockets"))))))))

(def ui-settings (fp/factory Settings))
