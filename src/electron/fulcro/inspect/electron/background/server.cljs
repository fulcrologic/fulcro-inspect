(ns fulcro.inspect.electron.background.server
  (:require
    [cljs.nodejs :as nodejs]
    [fulcro.inspect.remote.transit :as encode]))

(defonce Server (nodejs/require "socket.io"))
(goog-define SERVER_PORT 8237)

(defn process-client-message [client msg reply-fn]
  (js/console.log  (pr-str msg)))

(defn start! [{::keys [on-ready on-connect]}]
  (let [io (Server)]
    (js/console.log "Starting websocket server on port " SERVER_PORT)
    (.on io "connection" (fn [client]
                           (.on client "event"
                             (fn [data reply-fn]
                               (let [msg (some-> data encode/read)]
                                 (process-client-message client msg reply-fn))))
                           (js/console.log "Client connected")))
    (.listen io SERVER_PORT)))

