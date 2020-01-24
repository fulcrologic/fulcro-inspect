(ns fulcro.inspect.ui.settings
  (:require
    [fulcro.client.dom :as dom :refer [div button input a]]
    [fulcro.client.mutations :as m :refer [defmutation]]
    [fulcro.client.primitives :as fp :refer [defsc]]
    [fulcro.inspect.helpers :as db.h]))

(defmutation restart-websockets [_]
  (remote [env]
    (-> env :ast (assoc :key 'restart-websockets))))

(defsc Settings [this {:ui/keys [websocket-port]} {:keys [close-settings!]}]
  {:query             [::id :ui/websocket-port]
   :ident             (fn [] [::id "main"])
   :componentDidMount (fn []) ;; TASK: Load the port that was previously saved
   :initial-state     {:ui/websocket-port 8237}}
  (div :.ui.container {:style {:margin "1rem"}}
    (button :.ui.button.labeled.icon.basic.negative
      {:onClick close-settings!}
      (dom/i :.icon.close)
      "Close Settings")
    (div :.ui.divider.horizontal.hidden)
    (div :.ui.form.big
      (div :.fields
        (when (:fulcro.inspect.renderer/electron? (fp/shared this))
          (fp/fragment
            (div :.field.inline
              (dom/label "Websocket Port: ")
              (input {:value    websocket-port
                      :type     "number"
                      :onChange #(m/set-integer! this :ui/websocket-port :event %)}))
            (button :.ui.button.primary.positive
              {:onClick (fn []
                          (fp/transact! this `[(restart-websockets {:port ~websocket-port})]))}
              (dom/span :.ui.text.large "Restart Websockets"))))))))

(def ui-settings (fp/factory Settings))
