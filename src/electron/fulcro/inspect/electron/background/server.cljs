(ns fulcro.inspect.electron.background.server
  (:require
    ["electron" :refer [ipcMain]]
    ["electron-settings" :as settings]
    [cljs.core.async :as async :refer [put! take! >! <!] :refer-macros [go go-loop]]
    [cljs.nodejs :as nodejs]
    [clojure.set :refer [map-invert]]
    [clojure.string :as str]
    [fulcro.logging :as log]
    [fulcro.websockets.transit-packer :as tp]
    [goog.object :as gobj]
    [taoensso.encore :as enc]
    [taoensso.sente.server-adapters.express :as sente-express]
    [clojure.set :as set]))

(def http (nodejs/require "http"))
(def express (nodejs/require "express"))
(def express-ws (nodejs/require "express-ws"))
(def ws (nodejs/require "ws"))
(def body-parser (nodejs/require "body-parser"))

(defonce next-client-id (atom 1))
(defonce clients (atom {}))
(defonce app-uuids (atom {}))
(defn next-id [] (swap! next-client-id inc))
(defonce server-atom (atom nil))

(defn get-setting [k default] (or (.get settings k) default))
(defn set-setting! [k v] (.set settings k v))

;; server contains: {:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn connected-uids]}
(defonce channel-socket-server
  (let [packer (tp/make-packer {})]
    (sente-express/make-express-channel-socket-server!
      {:packer     packer
       :user-id-fn (fn initialize-user-id-from-request [req]
                     (log/debug "INIT_UID - req: " req) ;;TODO
                     (or
                       ;; TODO: return app-uuid if in request
                       (random-uuid)))})))

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
  (log/info "Starting express...")
  (let [express-app       (express)
        express-ws-server (express-ws express-app)]
    (wrap-defaults express-app routes channel-socket-server)
    (let [http-server (.listen express-app port)]
      {:express-app   express-app
       :ws-server     express-ws-server
       :sente-env     channel-socket-server
       :http-server   http-server
       :stop-fn       #(.close http-server)
       :port          port})))

(defn forward-client-message-to-renderer [web-contents msg client-id]
  (try
    (when web-contents
      (.send web-contents "event"
        #js {"fulcro-inspect-remote-message" msg
             "client-id"                     client-id}))
    (catch :default e
      (log/error e))))

(defn disconnect-client! [content-atom client-id]
  (swap! clients dissoc client-id)
  (enc/when-let [web-contents (some-> content-atom deref)
                 app-uuid     (get @app-uuids client-id)
                 message      {:type      :fulcro.inspect.client/dispose-app
                               :data      {:fulcro.inspect.core/app-uuid app-uuid}
                               :timestamp (js/Date.)}]
    (forward-client-message-to-renderer web-contents message client-id)))

(defn connect-client! [content-atom {:keys [ch-recv]} client-id]
  (go-loop []
    (when-some [data (<! ch-recv)] ;;TODO: ? further extraction ?
      (log/debug "connect-client! - data: " data) ;;TODO
      (when-let [web-contents (some-> content-atom deref)]
        (let [app-uuid (gobj/get data "uuid")
              msg      (gobj/get data "message")]
          (when-not (str/blank? app-uuid)
            (swap! app-uuids assoc client-id (uuid app-uuid)))
          (forward-client-message-to-renderer web-contents msg client-id))))
    (recur)))

(defn restart-ws!
  "Call to reset the websocket server"
  [{:keys [content-atom]}]
  (let [port (get-setting "port" 8237)
        {:as ws :keys [sente-env]} (start-web-server! port)]
    (when @server-atom
      ((:stop-fn @server-atom)))
    (reset! server-atom ws)
    (add-watch (:connected-uids sente-env)
      ::on-client-connect
      (fn [_ _ old new]
        (let [new-clients (set/difference new old)]
          (log/debug "new-clients:" new-clients) ;;TODO
          (map (partial connect-client! content-atom sente-env)
            new-clients))))
    (add-watch (:connected-uids sente-env)
      ::on-client-disconnect
      (fn [_ _ old new]
        (let [old-clients (set/difference old new)]
          (log/debug "old-clients:" old-clients) ;;TODO
          (map (partial disconnect-client! content-atom)
            old-clients))))
    (log/info "Fulcro Inspect Listening on port " port)))

(defn handle-message-from-renderer [env]
  (fn [_ msg]
    (if (gobj/get msg "restart")
      (let [port (gobj/get msg "port")]
        (set-setting! "port" port)
        (restart-ws! env))
      (let [{:keys [send-fn]} (:sente-env @server-atom)]
        ;(enc/when-let [app-uuid  (some-> msg (gobj/get "app-uuid") (encode/read))
        ;               client-id (get (map-invert @app-uuids) app-uuid)
        ;               client    (get @clients client-id))]
        (some-> msg
          (gobj/get "app-uuid")
          (send-fn [:fulcro.inspect/event msg]))))))

(defn start!
  "Call to start (or restart) the websocket server"
  [env]
  (set-setting! "ensure_settings_persisted" true)
  (.on ipcMain "event" (handle-message-from-renderer env))
  (restart-ws! env))
