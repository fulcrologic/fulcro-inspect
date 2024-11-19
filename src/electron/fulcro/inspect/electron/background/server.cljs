(ns fulcro.inspect.electron.background.server
  (:require
    ["electron" :refer [ipcMain]]
    ["electron-settings" :as settings]
    [cljs.pprint :refer [pprint]]
    [cljs.core.async :as async :refer [put! take! >! <!] :refer-macros [go go-loop]]
    [cljs.nodejs :as nodejs]
    [com.fulcrologic.fulcro.inspect.transit-packer :as tp]
    [fulcro.inspect.remote.transit :as encode]
    [goog.object :as gobj]
    [cognitect.transit :as transit]
    [com.cognitect.transit.types :as transit.types]
    [taoensso.encore :as enc]
    [taoensso.sente.server-adapters.express :as sente-express]
    [taoensso.timbre :as log]))

(defn pr-str-with-reader [^clj x]
  (if (transit/tagged-value? x)
    (str "#" (.-tag x) " " (.-rep x))
    (try
      (try
        (pr-str x)
        (catch :default _
          (str x)))
      (catch :default _
        "UNSUPPORTED VALUE"))))

(extend-protocol IPrintWithWriter
  transit.types/TaggedValue
  (-pr-writer [x writer _]
    (write-all writer (pr-str-with-reader x))))

(defn pprint-default-handler [x]
  (-write *out* (pr-str-with-reader x)))

(-add-method cljs.pprint/simple-dispatch :default pprint-default-handler)

(defonce channel-socket-server (atom nil))

(defonce app-uuid->client-id (atom {}))
(defonce client-id->app-uuid (atom {}))
(defonce content-atom (atom nil))
(defonce server-atom (atom nil))

(defn set-setting! [k v]
  (.set settings (str k) v))
(defn get-setting [k default]
  (let [c (async/chan)]
    (-> (.get settings (str k))
      (.then
        (fn [result]
          (async/go
            (async/>! c (if (nil? result) default result))
            (async/close! c)))))
    c))

(def default-port 8237)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Express Boilerplate Plumbing
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def http (nodejs/require "http"))
(def express (nodejs/require "express"))
(def express-ws (nodejs/require "express-ws"))
(def ws (nodejs/require "ws"))
(def cors (nodejs/require "cors"))
(def ^js body-parser (nodejs/require "body-parser"))

(defn routes [^js express-app {:keys [ajax-post-fn ajax-get-or-ws-handshake-fn]}]
  (doto express-app
    (.use (cors))
    (.ws "/chsk"
      (fn [ws req next]
        (ajax-get-or-ws-handshake-fn req nil nil
          {:websocket? true
           :websocket  ws})))
    (.get "/chsk" ajax-get-or-ws-handshake-fn)
    (.post "/chsk" ajax-post-fn)
    (.use (fn [^js req res next]
            (log/warn "Unhandled request: %s" (.-originalUrl req))
            (next)))))

(defn wrap-defaults [^js express-app routes ch-server]
  (doto express-app
    (.use (fn [^js req res next]
            (log/trace "Request: %s" (.-originalUrl req))
            (next)))
    (.use (.urlencoded body-parser #js {:extended false}))
    (routes ch-server)))

(defn start-web-server! [port]
  (log/trace "Starting express...")
  (let [^js express-app       (express)
        ^js express-ws-server (express-ws express-app)]
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

;; LANDMARK: messages to renderer that are not targeted at an inspector
(defn send-message-to-renderer! [msg]
  (when @content-atom
    (.send @content-atom "event"
      #js {:fulcro-inspect-remote-message (encode/write msg)})))

;; LANDMARK: ws-client message -> renderer
(defn forward-client-message-to-renderer! [msg client-id app-uuid]
  (log/trace "Inspect client->renderer msg-type:" (:type msg))
  (log/trace "Inspect client->renderer:" {:client-id client-id :app-uuid app-uuid})
  (try
    (if @content-atom
      (.send @content-atom "event"
        #js {:fulcro-inspect-remote-message (encode/write msg)
             :app-uuid                      (encode/write app-uuid)
             :client-id                     (encode/write client-id)})
      (log/warn "Message ignored. Content atom not ready."))
    (catch :default e
      (log/error e))))

(defn disconnect-client! [client-id]
  (log/trace "Attempting to disconnect client with id:" client-id)
  (enc/when-let [app-uuid (get @client-id->app-uuid client-id)
                 message  {:type      :fulcro.inspect.client/dispose-app
                           :data      {:fulcro.inspect.core/app-uuid app-uuid}
                           :timestamp (js/Date.)}]
    (swap! client-id->app-uuid dissoc client-id)
    (swap! app-uuid->client-id dissoc app-uuid)
    (forward-client-message-to-renderer! message client-id app-uuid)))

(defn ?record-app-uuid-mapping! [app-uuid client-id]
  (when (and (uuid? app-uuid) (not (contains? @app-uuid->client-id app-uuid)))
    (log/trace "Saving app uuid client-id association: "
      {:client-id client-id
       :app-uuid  app-uuid})
    (swap! client-id->app-uuid assoc client-id app-uuid)
    (swap! app-uuid->client-id assoc app-uuid client-id)))

(defn connect-client! [client-id]
  (let [{:keys [send-fn]} @channel-socket-server
        msg {:type :fulcro.inspect.client/request-page-apps :data {}}]
    (send-fn client-id [:fulcro.inspect/event msg])))

(defn start-ws! []
  (when-not @channel-socket-server
    (reset! channel-socket-server
      (sente-express/make-express-channel-socket-server!
        {:packer        (tp/make-packer {})
         :csrf-token-fn nil
         :user-id-fn    :client-id})))
  (go-loop []
    (when-some [{:keys [client-id event]} (<! (:ch-recv @channel-socket-server))]
      (let [[event-type event-data] event]
        (log/trace "Server received:" event-type)
        (log/trace "-> with event data:" event-data)
        (case event-type
          :fulcro.inspect/message
          (let [app-uuid (log/spy :trace (-> event-data :data :fulcro.inspect.core/app-uuid))]
            (?record-app-uuid-mapping! app-uuid client-id)
            (forward-client-message-to-renderer! event-data client-id app-uuid))
          :chsk/uidport-close
          (disconnect-client! client-id)
          :chsk/uidport-open
          (connect-client! client-id)
          :chsk/ws-ping
          (log/trace "ws-ping from client:" client-id)
          #_else
          (log/trace "Unsupported event:" event "from client:" client-id))))
    (recur))
  (async/go
    (let [saved-port (async/<! (get-setting :setting/websocket-port ::default))
          port       (async/<! (get-setting :setting/websocket-port default-port))]
      (when (= ::default saved-port)
        (set-setting! :setting/websocket-port default-port))
      (log/info "Fulcro Inspect Listening on port " port)
      (reset! server-atom (start-web-server! port)))))

(defn restart! []
  (log/info "Stopping websockets.")
  (when @server-atom
    ((:stop-fn @server-atom)))
  (reset! channel-socket-server nil)
  (reset! server-atom nil)
  (start-ws!))

(defn forward-renderer-message-to-client! [{:as msg :keys [app-uuid fulcro-inspect-devtool-message]}]
  (let [app-uuid (or app-uuid (get-in fulcro-inspect-devtool-message [:data :app-uuid]))]
    (log/trace "renderer->client message:" msg)
    (log/trace "renderer->client devtool-message type:" (:type fulcro-inspect-devtool-message))
    (log/trace "renderer->client devtool-message:" fulcro-inspect-devtool-message)
    (if-not app-uuid
      (log/warn "Unable to find app-uuid in message:" msg)
      (let [client-id (some->> app-uuid
                        (get @app-uuid->client-id)
                        (log/spy :trace "client-id:"))]
        (if-not client-id
          (log/warn "Could not find app-uuid in registered apps:"
            {:app-uuid app-uuid :app-uuid->client-id @app-uuid->client-id})
          (let [{:keys [send-fn]} @channel-socket-server]
            (log/trace "Sending to application" fulcro-inspect-devtool-message)
            (send-fn client-id [:fulcro.inspect/event fulcro-inspect-devtool-message])))))))

(defn handle-save-settings [{:keys [fulcro-inspect-devtool-message]}]
  (when (= :fulcro.inspect.client/save-settings (:type fulcro-inspect-devtool-message))
    (doseq [[k v] (:data fulcro-inspect-devtool-message)]
      (log/trace "Saving setting:" k "=>" v)
      (set-setting! k v)
      (case k
        :setting/websocket-port
        (do
          (log/info "Received restart message!")
          (restart!))
        #_else nil))
    :ok))

(defn handle-load-settings [{:keys [fulcro-inspect-devtool-message]}]
  (when (= :fulcro.inspect.client/load-settings (:type fulcro-inspect-devtool-message))
    (async/go
      (let [msg-data (:data fulcro-inspect-devtool-message)
            msg-id   (:fulcro.inspect.ui-parser/msg-id msg-data)
            query    (:query msg-data)
            vs       (async/<! (async/reduce
                                 (fn [acc v]
                                   (conj acc v))
                                 []
                                 (async/map identity
                                   (mapv #(get-setting % ::not-found) query))))
            settings (zipmap query vs)
            response {:type :fulcro.inspect.client/message-response
                      :data {:fulcro.inspect.ui-parser/msg-id       msg-id
                             :fulcro.inspect.ui-parser/msg-response settings}}]
        (send-message-to-renderer! response)))
    :ok))

(defn parse-message [message]
  (->> (js->clj message :keywordize-keys true)
    (map (fn [[k v]] [k (encode/read v)]))
    (into {})))

(defn start!
  "Called on overall Inspect App startup (once)"
  [^js web-content]
  (set-setting! "ensure_settings_persisted" true)
  (log/info "Setting web content to" web-content)
  (reset! content-atom web-content)
  (start-ws!)
  (.on web-content "dom-ready"
    (fn on-inspect-reload-request-app-state []
      (let [{:keys [send-fn]} @channel-socket-server
            msg {:type :fulcro.inspect.client/request-page-apps
                 :data {}}]
        (doseq [[client-id _] @client-id->app-uuid]
          (send-fn client-id [:fulcro.inspect/event msg])))))
  ;; LANDMARK: Hook up of incoming messages from Electron renderer
  (.on ipcMain "event"
    (fn handle-renderer-messages [_ message]
      (log/trace "Server received message from renderer")
      (let [msg (parse-message message)]
        (or
          (handle-save-settings msg)
          (handle-load-settings msg)
          (forward-renderer-message-to-client! msg))))))
