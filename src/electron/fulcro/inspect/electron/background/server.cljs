(ns fulcro.inspect.electron.background.server
  (:require
    [cljs.nodejs :as nodejs]
    ["electron" :refer [ipcMain]]
    ["electron-settings" :as settings]
    ["socket.io" :as Server]
    [goog.object :as gobj]
    [clojure.set :refer [map-invert]]
    [fulcro.inspect.remote.transit :as encode]
    [taoensso.encore :as enc]
    [clojure.string :as str]))

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
  (.set settings "ensure_settings_persisted" 42)
  (let [io   (Server)
        port (or (.get settings "port") 8237)]
    (when @server-atom
      (.close @server-atom))
    (reset! server-atom io)
    (.on io "connect"
      (fn [client]
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
                  (when-not (str/blank? app-uuid)
                    (swap! app-uuids assoc id (uuid app-uuid)))
                  (process-client-message web-contents msg id))))))))
    (js/console.log "Fulcro Inspect Listening on port " port)
    (.listen io port)))

(defn start!
  "Call to start (or restart) the websocket server"
  [{:keys [content-atom] :as env}]
  (.on ipcMain "event"
    (fn [evt arg]
      (if (gobj/get arg "restart")
        (let [port (gobj/get arg "port")]
          (.set settings "port" port)
          (restart-ws! env))
        (enc/when-let [app-uuid (some-> arg (gobj/get "app-uuid") (encode/read))
                       client-id (get (map-invert @app-uuids) app-uuid)
                       client (get @clients client-id)]
          (.emit client "event" arg)))))
  (restart-ws! env))
