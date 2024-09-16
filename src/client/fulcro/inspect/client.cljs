(ns fulcro.inspect.client
  (:require [cljs.core.async :as async]
            [clojure.set :as set]
            [com.fulcrologic.fulcro-css.css :as css]
            [com.fulcrologic.fulcro.application :as fulcro]
            [com.fulcrologic.fulcro-css.localized-dom :as dom]
            [com.fulcrologic.fulcro.mutations :as fm]
            [fulcro.client.network :as f.network]
            [com.fulcrologic.fulcro.components :as fp]
            [fulcro.inspect.lib.diff :as diff]
            [fulcro.inspect.lib.misc :as misc]
            [fulcro.inspect.lib.version :as version]
            [fulcro.inspect.remote.transit :as encode]
            [fulcro.inspect.ui.dom-history-viewer :as dom-history]
            [fulcro.inspect.ui.element-picker :as picker]
            [fulcro.inspect.ui.helpers :as ui.h]
            [goog.object :as gobj]))

(defonce started?* (atom false))

(defonce tools-app* (atom nil))
(defonce apps* (atom {}))
(defonce send-ch (async/chan (async/dropping-buffer 1024)))

(def app-uuid-key :fulcro.inspect.core/app-uuid)

(defn post-message [type data]
  (async/put! send-ch [type data]))

(declare handle-devtool-message)

(defn event-data [event]
  (some-> event (gobj/getValueByKeys "data" "fulcro-inspect-devtool-message") encode/read))

(defn start-send-message-loop []
  (async/go-loop []
    (when-let [[type data] (async/<! send-ch)]
      (.postMessage js/window (clj->js {:fulcro-inspect-remote-message (encode/write {:type type :data data :timestamp (js/Date.)})}) "*")
      (recur))))

(defn listen-local-messages []
  (.addEventListener js/window "message"
    (fn [event]
      (cond
        (and (identical? (.-source event) js/window)
             (gobj/getValueByKeys event "data" "fulcro-inspect-devtool-message"))
        (handle-devtool-message (event-data event))

        (and (identical? (.-source event) js/window)
             (gobj/getValueByKeys event "data" "fulcro-inspect-start-consume"))
        (start-send-message-loop)))
    false))

(defn app-uuid [reconciler]
  (some-> reconciler fp/app-state deref app-uuid-key))

(defn app-id [reconciler]
  (or (some-> reconciler fp/app-state deref :fulcro.inspect.core/app-id)
      (some-> reconciler fp/app-root ui.h/react-display-name)))

(defn inspect-network-init [network app]
  (-> network :options ::app* (reset! app)))

(defn transact-inspector!
  ([tx]
   (post-message ::transact-inspector {::tx tx}))
  ([ref tx]
   (post-message ::transact-inspector {::tx-ref ref ::tx tx})))

(def MAX_HISTORY_SIZE 100)

(defn update-state-history [app state]
  (swap! (-> app :reconciler :state) update ::state-history
    #(misc/fixed-size-assoc MAX_HISTORY_SIZE % (hash state) state)))

(defn db-update [app app-uuid old-state new-state]
  (update-state-history app new-state)
  (let [diff (diff/diff old-state new-state)]
    (post-message ::db-update {app-uuid-key      app-uuid
                               ::prev-state-hash (hash old-state)
                               ::state-hash      (hash new-state)
                               ::state-delta     diff
                               ;::state           new-state
                               })))

(defn db-from-history [app state-hash]
  (some-> app :reconciler :state deref ::state-history (get state-hash)))

(defn dispose-app [app-uuid]
  (swap! apps* dissoc app-uuid)
  (post-message ::dispose-app {app-uuid-key app-uuid}))

(defn set-active-app [app-uuid]
  (post-message ::set-active-app {app-uuid-key app-uuid}))

(defn inspect-app [{:keys [reconciler networking] :as app}]
  (let [state*   (some-> app :reconciler :config :state)
        app-uuid (random-uuid)]

    (doseq [[_ n] networking]
      (inspect-network-init n app))

    (swap! apps* assoc app-uuid app)

    (update-state-history app @state*)
    (post-message ::init-app {app-uuid-key                app-uuid
                              :fulcro.inspect.core/app-id (app-id reconciler)
                              ::remotes                   (sort-by (juxt #(not= :remote %) str) (keys networking))
                              ::initial-state             @state*
                              ::state-hash                (hash @state*)})

    (add-watch state* app-uuid
      #(db-update app app-uuid %3 %4))

    (swap! state* assoc app-uuid-key app-uuid)

    app))

(defn inspect-tx [{:keys [reconciler] :as env}
                  {:fulcro.history/keys [db-before db-after]
                   :as                  info}]
  (if (fp/app-root reconciler)
    (let [tx       (-> (merge info (select-keys env [:ref :component]))
                       (update :component #(gobj/get (fp/react-type %) "displayName"))
                       (set/rename-keys {:ref :ident-ref})
                       (dissoc :old-state :new-state :tx :ret
                         :fulcro.history/db-before :fulcro.history/db-after)
                       (assoc :fulcro.history/db-before-hash (hash db-before)
                              :fulcro.history/db-after-hash (hash db-after)))
          app-uuid (app-uuid reconciler)]
      ; ensure app is initialized
      (when (-> reconciler fp/app-state deref :fulcro.inspect.core/app-uuid)
        (post-message ::new-client-transaction {app-uuid-key app-uuid
                                                ::tx         tx})))))

;;; network

(defrecord TransformNetwork [network options]
  f.network/NetworkBehavior
  (serialize-requests? [this]
    (try
      (f.network/serialize-requests? network)
      (catch :default _ true)))

  f.network/FulcroNetwork
  (send [_ edn ok error]
    (let [{::keys [transform-query transform-response transform-error app*]
           :or    {transform-query    (fn [_ x] x)
                   transform-response (fn [_ x] x)
                   transform-error    (fn [_ x] x)}} options
          req-id (random-uuid)
          env    {::request-id req-id
                  ::app        @app*}]
      (if-let [edn' (transform-query env edn)]
        (f.network/send network edn'
          #(->> % (transform-response env) ok)
          #(->> % (transform-error env) error))
        (ok nil))))

  (start [this]
    (try
      (f.network/start network)
      (catch ::default e
        (js/console.log "Error starting sub network" e)))
    this))

(defn transform-network [network options]
  (->TransformNetwork network (assoc options ::app* (atom nil))))

(defrecord TransformNetworkI [network options]
  f.network/NetworkBehavior
  (serialize-requests? [this]
    (if (satisfies? f.network/NetworkBehavior network)
      (f.network/serialize-requests? network)
      true))

  f.network/FulcroRemoteI
  (transmit [_ {::f.network/keys [edn ok-handler error-handler progress-handler]}]
    (let [{::keys [transform-query transform-response transform-error app*]
           :or    {transform-query    (fn [_ x] x)
                   transform-response (fn [_ x] x)
                   transform-error    (fn [_ x] x)}} options
          req-id (random-uuid)
          env    {::request-id req-id
                  ::app        @app*}]
      (if-let [edn' (transform-query env edn)]
        (f.network/transmit network
          {::f.network/edn              edn'
           ::f.network/ok-handler       #(->> % (transform-response env) ok-handler)
           ::f.network/error-handler    #(->> % (transform-error env) error-handler)
           ::f.network/progress-handler progress-handler})
        (ok-handler nil))))

  (abort [_ abort-id] (f.network/abort network abort-id)))

(defn transform-network-i [network options]
  (->TransformNetworkI network (assoc options ::app* (atom nil))))

(defn inspect-network
  ([remote network]
   (let [ts {::transform-query
             (fn [{::keys [request-id app]} edn]
               (let [start    (js/Date.)
                     app-uuid (app-uuid (:reconciler app))]
                 (transact-inspector! [:fulcro.inspect.ui.network/history-id [app-uuid-key app-uuid]]
                   [`(fulcro.inspect.ui.network/request-start ~{:fulcro.inspect.ui.network/remote             remote
                                                                :fulcro.inspect.ui.network/request-id         request-id
                                                                :fulcro.inspect.ui.network/request-started-at start
                                                                :fulcro.inspect.ui.network/request-edn        edn})]))
               edn)

             ::transform-response
             (fn [{::keys [request-id app]} response]
               (let [finished (js/Date.)
                     app-uuid (app-uuid (:reconciler app))]
                 (transact-inspector! [:fulcro.inspect.ui.network/history-id [app-uuid-key app-uuid]]
                   [`(fulcro.inspect.ui.network/request-finish ~{:fulcro.inspect.ui.network/request-id          request-id
                                                                 :fulcro.inspect.ui.network/request-finished-at finished
                                                                 :fulcro.inspect.ui.network/response-edn        response})]))
               response)

             ::transform-error
             (fn [{::keys [request-id app]} error]
               (let [finished (js/Date.)
                     app-uuid (app-uuid (:reconciler app))]
                 (transact-inspector! [:fulcro.inspect.ui.network/history-id [app-uuid-key app-uuid]]
                   [`(fulcro.inspect.ui.network/request-finish ~{:fulcro.inspect.ui.network/request-id          request-id
                                                                 :fulcro.inspect.ui.network/request-finished-at finished
                                                                 :fulcro.inspect.ui.network/error               error})]))
               error)}]
     (cond
       (implements? f.network/FulcroNetwork network)
       (transform-network network ts)

       (implements? f.network/FulcroRemoteI network)
       (transform-network-i network
         (update ts ::transform-response (fn [tr] (fn [env {:keys [body] :as response}]
                                                    (tr env body)
                                                    response))))

       :else
       (js/console.warn "Invalid network" {:network network})))))

;; LANDMARK: This is how the inspect UI talks to whichever chrome (browser extension or electron) is hosting the tool
(defn handle-devtool-message [{:keys [type data]}]
  (case type
    ::restart-websocket
    (post-message ::restart-websocket {})

    ::request-page-apps
    (doseq [{:keys [reconciler networking]} (vals @apps*)]
      (post-message ::init-app {app-uuid-key                (app-uuid reconciler)
                                :fulcro.inspect.core/app-id (app-id reconciler)
                                ::remotes                   (sort-by (juxt #(not= :remote %) str) (keys networking))
                                ::initial-state             @(fp/app-state reconciler)
                                ::state-hash                (hash @(fp/app-state reconciler))}))

    ::reset-app-state
    (let [{:keys                     [target-state]
           :fulcro.inspect.core/keys [app-uuid]} data]
      (if-let [{:keys [reconciler]} (get @apps* app-uuid)]
        (do
          (if target-state
            (let [target-state (assoc target-state :fulcro.inspect.core/app-uuid app-uuid)]
              (some-> reconciler fp/app-state (reset! target-state))))
          (js/setTimeout #(fp/force-root-render! reconciler) 10))
        (js/console.log "Reset app on invalid uuid" app-uuid)))

    ::transact
    (let [{:keys                     [tx tx-ref]
           :fulcro.inspect.core/keys [app-uuid]} data]
      (if-let [{:keys [reconciler]} (get @apps* app-uuid)]
        (if tx-ref
          (fp/transact! reconciler tx-ref tx)
          (fp/transact! reconciler tx))
        (js/console.log "Transact on invalid uuid" app-uuid)))

    ::pick-element
    (let [{:fulcro.inspect.core/keys [app-uuid]} data]
      (picker/pick-element
        {:fulcro.inspect.core/app-uuid
         app-uuid
         ::picker/on-pick
         (fn [comp]
           (if comp
             (let [details (picker/inspect-component comp)]
               (transact-inspector! [:fulcro.inspect.ui.element/panel-id [:fulcro.inspect.core/app-uuid app-uuid]]
                 [`(fulcro.inspect.ui.element/set-element ~details)]))
             (transact-inspector! [:fulcro.inspect.ui.element/panel-id [:fulcro.inspect.core/app-uuid app-uuid]]
               [`(fm/set-props {:ui/picking? false})])))}))

    ::show-dom-preview
    (let [{:fulcro.inspect.core/keys [app-uuid]} data
          app                    (some-> @apps* (get app-uuid))
          app-state              (db-from-history app (::state-hash data))
          reconciler             (some-> @apps* (get app-uuid) :reconciler)
          app-root-class         (fp/react-type (fp/app-root reconciler))
          app-root-class-factory (fp/factory app-root-class)
          root-query             (fp/get-query app-root-class app-state)
          view-tree              (fp/db->tree root-query app-state app-state)
          data                   (assoc data :state (vary-meta view-tree assoc :render-fn app-root-class-factory))]
      (fp/transact! (:reconciler @tools-app*) [::dom-history/dom-viewer :singleton] [`(dom-history/show-dom-preview ~data)]))

    ::hide-dom-preview
    (fp/transact! (:reconciler @tools-app*) [::dom-history/dom-viewer :singleton] [`(dom-history/hide-dom-preview {})])

    ::network-request
    (let [{:keys                          [query]
           ::keys                         [remote]
           :fulcro.inspect.ui-parser/keys [msg-id]
           :fulcro.inspect.core/keys      [app-uuid]} data]
      (when-let [app (get @apps* app-uuid)]
        (let [remote           (-> app :networking remote)
              response-handler (fn [res]
                                 (post-message ::message-response {:fulcro.inspect.ui-parser/msg-id       msg-id
                                                                   :fulcro.inspect.ui-parser/msg-response res}))]
          (cond
            (implements? f.network/FulcroNetwork remote)
            (f.network/send remote query response-handler response-handler)

            (implements? f.network/FulcroRemoteI remote)
            (f.network/transmit remote
              {::f.network/edn           query
               ::f.network/ok-handler    (comp response-handler :body)
               ::f.network/error-handler (comp response-handler :body)})))))

    ::console-log
    (let [{:keys [log log-js warn error]} data]
      (cond
        log
        (js/console.log log)

        log-js
        (js/console.log (clj->js log-js))

        warn
        (js/console.warn warn)

        error
        (js/console.error error)))

    ::check-client-version
    (post-message ::client-version {:version version/last-inspect-version})

    (js/console.log "Unknown message" type)))

(fp/defsc ClientRoot [this {:keys [history]}]
  {:initial-state {:history {}}
   :ident         (fn [] [::root "main"])
   :query         [{:history (fp/get-query dom-history/DOMHistoryView)}]
   :css-include   [picker/MarkerCSS dom-history/DOMHistoryView]}

  (dom/div
    (css/style-element this)
    (dom-history/ui-dom-history-view history)))

(defn install [_]
  (js/document.documentElement.setAttribute "__fulcro-inspect-remote-installed__" true)

  (when-not @started?*
    (js/console.log "Installing Fulcro Inspect" {})

    (reset! started?* true)

    (let [app  (fulcro/new-fulcro-client)
          node (js/document.createElement "div")]
      (js/document.body.appendChild node)
      (reset! tools-app* (fulcro/mount app ClientRoot node)))

    (fulcro/register-tool
      {::fulcro/tool-id
       ::fulcro-inspect-remote

       ::fulcro/app-started
       inspect-app

       ::fulcro/network-wrapper
       (fn [networks]
         (into {} (map (fn [[k v]] [k (inspect-network k v)])) networks))

       ::fulcro/tx-listen
       inspect-tx})

    (listen-local-messages)))
