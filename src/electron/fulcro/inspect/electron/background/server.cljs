(ns fulcro.inspect.electron.background.server
  (:require
    [cljs.nodejs :as nodejs]
    ["electron" :refer [ipcMain]]
    ["electron-settings" :as settings]
    ["socket.io" :as Server]
    [goog.object :as gobj]
    [fulcro.inspect.remote.transit :as encode]
    [taoensso.encore :as enc]))

(defonce server-port (atom (or (.get settings "port") 8237)))
(defonce next-client-id (atom 1))
(defonce clients (atom {}))
(defonce app-uuids (atom {}))
(defn next-id [] (swap! next-client-id inc))
(defonce server-atom (atom nil))

(defn process-client-message [web-contents msg client-id]
  (try
    (when web-contents
      (.send web-contents "event" #js {"fulcro-inspect-remote-message" msg
                                       "client-id"                     client-id}))
    (catch :default e
      (js/console.error e))))

(defn restart-ws!
  "Call to reset the websocket server"
  [{:keys [content-atom]}]
  (let [io (Server)]
    (when @server-atom
      (.close @server-atom))
    (reset! server-atom io)
    (.on io "connect" (fn [client]
                        (let [id (next-id)]
                          (swap! clients assoc id client)
                          (.on client "disconnect"
                            (fn [data reply-fn]
                              (swap! clients dissoc id)
                              (enc/when-let [web-contents (some-> content-atom deref)
                                             app-uuid     (get @app-uuids id)
                                             message      (encode/write {:type      :fulcro.inspect.client/dispose-app
                                                                         :data      {:fulcro.inspect.core/app-uuid app-uuid}
                                                                         :timestamp (js/Date.)})]
                                (process-client-message web-contents message id))))
                          (.on client "event"
                            (fn [data reply-fn]
                              (when-let [web-contents (some-> content-atom deref)]
                                (let [app-uuid (gobj/get data "uuid")
                                      msg      (gobj/get data "message")]
                                  (when app-uuid
                                    (swap! app-uuids assoc id (uuid app-uuid)))
                                  (process-client-message web-contents msg id))))))))
    (js/console.log "Fulcro Inspect Listening on port " @server-port)
    (.listen io @server-port)))

(defn start!
  "Call to start (or restart) the websocket server"
  [{:keys [content-atom] :as env}]
  (.on ipcMain "event" (fn [evt arg]
                         (if (gobj/get arg "restart")
                           (let [port (gobj/get arg "port")]
                             (reset! server-port port)
                             (.set settings "port" port)
                             (restart-ws! env))
                           (enc/when-let [connection-id (gobj/get arg "client-connection-id")
                                          client        (get @clients connection-id)]
                             (.emit client "event" arg)))))
  (restart-ws! env))
