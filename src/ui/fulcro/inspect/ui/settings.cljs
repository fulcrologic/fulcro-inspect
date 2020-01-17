(ns fulcro.inspect.ui.settings
  (:require
    [fulcro.client.dom :as dom :refer [div button input a]]
    [fulcro.client.mutations :as m :refer [defmutation]]
    [fulcro.client.primitives :as fp :refer [defsc]]
    [fulcro.inspect.helpers :as db.h]))

(defmutation restart-websockets [_]
  (remote [env]
    (db.h/remote-mutation env 'restart-websockets)))

;; FIXME: This needs to be usable before apps connect, since the apps might be using a diff port, and this sets it.
(defsc Settings [this {:ui/keys [websocket-port]}]
  {:query             [::id :ui/websocket-port]
   :ident             [::id ::id]
   :componentDidMount (fn []
                        ;; TODO: Load the port that was previously saved
                        )
   :initial-state     (fn [_] {:ui/websocket-port 8237})}
  (div :.ui.container {:style {:margin "1rem"}}
    ;; FIXME: There is NO websocket port in chrome.
    (div :.ui.form.big
      (div :.fields
        (div :.field.inline
          (dom/label "Websocket Port: ")
          (input {:value    websocket-port
                  :type     "number"
                  :onChange #(m/set-integer! this :ui/websocket-port :event %)}))
        (button :.ui.button.primary.red
          {:onClick (fn [] (fp/transact! this `[(restart-websockets {:port ~websocket-port})]))}
          (dom/span :.ui.text.large "Restart Websockets"))))))

(def ui-settings (fp/factory Settings))
