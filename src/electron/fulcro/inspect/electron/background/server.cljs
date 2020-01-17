(ns fulcro.inspect.electron.background.server
  (:require
    ["electron" :refer [ipcMain]]
    ["electron-settings" :as settings]
    [cljs.core.async :as async :refer [put! take! >! <!] :refer-macros [go go-loop]]
    [cljs.nodejs :as nodejs]
    [clojure.set :refer [map-invert]]
    [clojure.string :as str]
    [clojure.pprint :refer [pprint]]
    [fulcro.logging :as log]
    [fulcro.websockets.transit-packer :as tp]
    [goog.object :as gobj]
    [taoensso.encore :as enc]
    [taoensso.sente.server-adapters.express :as sente-express]
    [clojure.set :as set]
    [fulcro.inspect.remote.transit :as encode]))

(defonce channel-socket-server (atom nil))

(defn spy [msg v]
  (js/console.log msg (with-out-str (pprint v)))
  v)

(def http (nodejs/require "http"))
(def express (nodejs/require "express"))
(def express-ws (nodejs/require "express-ws"))
(def ws (nodejs/require "ws"))
(def body-parser (nodejs/require "body-parser"))

(defonce content-atom (atom nil))
(defonce client-id->app-uuid (atom {}))
(defonce app-uuid->client-id (atom {}))
(defonce server-atom (atom nil))

(defn get-setting [k default] (or (.get settings k) default))
(defn set-setting! [k v] (.set settings k v))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Express Boilerplate Plumbing
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn routes [express-app {:keys [ajax-post-fn ajax-get-or-ws-handshake-fn]}]
  (doto express-app
    (.ws "/chsk"
      (fn [ws req next]
        (ajax-get-or-ws-handshake-fn req nil nil
          {:websocket? true
           :websocket  ws})))
    (.get "/chsk" ajax-get-or-ws-handshake-fn)
    (.post "/chsk" ajax-post-fn)
    (.use (fn [req res next]
            (log/warn "Unhandled request: %s" (.-originalUrl req))
            (next)))))

(defn wrap-defaults [express-app routes ch-server]
  (doto express-app
    (.use (fn [req res next]
            (log/trace "Request: %s" (.-originalUrl req))
            (next)))
    (.use (.urlencoded body-parser #js {:extended false}))
    (routes ch-server)))

(defn start-web-server! [port]
  (js/console.log "Starting express...")
  (let [express-app       (express)
        express-ws-server (express-ws express-app)]
    (wrap-defaults express-app routes @channel-socket-server)
    (let [http-server (.listen express-app port)]
      (reset! server-atom
        {:express-app express-app
         :ws-server   express-ws-server
         :http-server http-server
         :stop-fn     #(.close http-server)
         :port        port}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Real Comms Logic:
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; LANDMARK: ws-client message -> renderer
(defn forward-client-message-to-renderer [msg client-id app-uuid]
  (spy "Inspect client->renderer message" msg)
  (try
    (when @content-atom
      (.send @content-atom "event"
        #js {"fulcro-inspect-remote-message" (encode/write msg)
             "app-uuid"                      (encode/write app-uuid)
             "client-id"                     (encode/write client-id)}))
    (catch :default e
      (log/error e))))

(defn disconnect-client! [client-id]
  (enc/when-let [app-uuid (get @client-id->app-uuid client-id)
                 message  {:type      :fulcro.inspect.client/dispose-app
                           :data      {:fulcro.inspect.core/app-uuid app-uuid}
                           :timestamp (js/Date.)}]
    (swap! client-id->app-uuid dissoc client-id)
    (swap! app-uuid->client-id dissoc app-uuid)
    (forward-client-message-to-renderer message client-id app-uuid)))

(defn start-ws! []
  (when-not @channel-socket-server
    (reset! channel-socket-server
      (let [packer (tp/make-packer {})
            {:keys [ch-recv send-fn connected-uids] :as result} (sente-express/make-express-channel-socket-server!
                                                                  {:packer        packer
                                                                   :csrf-token-fn nil
                                                                   :user-id-fn    (fn initialize-user-id-from-request [req]
                                                                                    (:client-id req))})]

        (go-loop []
          (when-some [{:keys [client-id event] :as data} (<! ch-recv)]
            (let [[event-type event-data] event]
              (case event-type
                :inspect/message (let [app-uuid (-> event-data :data :fulcro.inspect.core/app-uuid)]
                                   (when (and (uuid? app-uuid) (not (contains? @app-uuid->client-id app-uuid)))
                                     (js/console.log "Saving app uuid client-id association")
                                     (swap! client-id->app-uuid assoc client-id app-uuid)
                                     (swap! app-uuid->client-id assoc app-uuid client-id))
                                   (forward-client-message-to-renderer event-data client-id app-uuid))
                (spy "Unsupported event" event))))
          (recur))
        result)))
  (let [port (get-setting "port" 8237)]
    (js/console.log "Fulcro Inspect Listening on port " port)
    (reset! server-atom (start-web-server! port))))

(defn restart! []
  (js/console.log "Stopping websockets.")
  (when @server-atom
    ((:stop-fn @server-atom)))
  (reset! channel-socket-server nil)
  (reset! server-atom nil)
  (start-ws!))

(defn handle-message-from-renderer [_ msg]
  (js/console.log "Inspect renderer wants to send to client: " msg)
  (if (gobj/get msg "restart")
    (let [port (gobj/get msg "port")]
      (set-setting! "port" port)
      (restart!))
    (let [{:keys [send-fn]} @channel-socket-server]
      ;; TODO: IF it has an app UUID, then we'll send it
      ;(enc/when-let [app-uuid  (some-> msg (gobj/get "app-uuid") (encode/read))
      ;               client-id (get (map-invert @app-uuids) app-uuid)
      ;               client    (get @clients client-id))]
      (some-> msg
        (gobj/get "app-uuid")
        (encode/read)
        (send-fn [:fulcro.inspect/event msg])))))

(defn start!
  "Called on overall Inspect App startup (once)"
  [web-content]
  (set-setting! "ensure_settings_persisted" true)
  (reset! content-atom web-content)
  (start-ws!)
  ;; LANDMARK: Hook up of incoming messages from Electron renderer
  (.on ipcMain "event" handle-message-from-renderer))


