(ns fulcro.inspect.electron.background.server
  (:require
    [cljs.nodejs :as nodejs]
    ["electron" :refer [ipcMain]]
    [goog.object :as gobj]
    [fulcro.inspect.remote.transit :as encode]))

(defonce Server (nodejs/require "socket.io"))
(goog-define SERVER_PORT 8237)

(defn process-client-message [web-contents msg reply-fn]
  (js/console.log "forwarding ws message to rendering client")
  (try
    (when web-contents
      (.send web-contents "event" #js {"fulcro-inspect-remote-message" msg}))
    (catch :default e
      (js/console.error e))))

(defn start! [{:keys [content-atom]}]
  (let [io (Server)]
    (js/console.log "Starting websocket server on port " SERVER_PORT)
    (.on io "connection" (fn [client]
                           (.on client "event"
                             (fn [data reply-fn]
                               (when-let [web-contents (some-> content-atom deref)]
                                 (process-client-message web-contents data reply-fn))))
                           (js/console.log "Client connected")))
    (.listen io SERVER_PORT)))

