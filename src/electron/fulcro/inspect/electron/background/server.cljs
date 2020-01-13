(ns fulcro.inspect.electron.background.server
  (:require
    [cljs.nodejs :as nodejs]
    ["electron" :refer [ipcMain]]
    ["socket.io" :as Server]
    [goog.object :as gobj]
    [fulcro.inspect.remote.transit :as encode]
    [taoensso.encore :as enc]))

(goog-define SERVER_PORT 8237)

(defonce next-client-id (atom 1))
(defonce clients (atom {}))
(defn next-id [] (swap! next-client-id inc))

(defn process-client-message [web-contents msg client-id]
  (try
    (when web-contents
      (.send web-contents "event" #js {"fulcro-inspect-remote-message" msg
                                       "client-id"                     client-id}))
    (catch :default e
      (js/console.error e))))

(defn start! [{:keys [content-atom]}]
  (let [io (Server)]
    (.on ipcMain "event" (fn [evt arg]
                           (enc/when-let [connection-id (gobj/get arg "client-connection-id")
                                          client        (get @clients connection-id)]
                             (js/console.log "Sending message to client via websocket " connection-id)
                             (.emit client "event" arg))))
    (.on io "connect" (fn [client]
                        (let [id (next-id)]
                          (swap! clients assoc id client)
                          (.on client "event"
                            (fn [data reply-fn]
                              (when-let [web-contents (some-> content-atom deref)]
                                (process-client-message web-contents data id)))))))
    (.listen io SERVER_PORT)))

