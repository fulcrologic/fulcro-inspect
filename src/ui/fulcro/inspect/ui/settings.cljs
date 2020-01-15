(ns fulcro.inspect.ui.settings
  (:require
    [fulcro.client.dom :as dom :refer [div button input a]]
    [fulcro.client.mutations :as m :refer [defmutation]]
    [fulcro.client.primitives :as fp :refer [defsc]]
    [goog.object :as gobj]))

(defonce electron (js/require "electron"))
(def ipcRenderer (gobj/get electron "ipcRenderer"))
(defonce settings (js/require "electron-settings"))

(defsc Settings [this {:ui/keys [websocket-port]}]
  {:query         [::id :ui/websocket-port]
   :ident         [::id ::id]
   :initial-state (fn [_] {:ui/websocket-port (or (.get settings "port") 8237)})}
  (div :.ui.container
    (dom/div (dom/label "Websocket Port: ")
      (dom/input {:value    websocket-port
                  :type     "number"
                  :onChange #(m/set-integer! this :ui/websocket-port :event %)})
      (dom/button {:onClick (fn []
                              (.send ipcRenderer "event"
                                #js {:port    websocket-port
                                     :restart true}))}
        "Restart Websockets"))))

(def ui-settings (fp/factory Settings))
