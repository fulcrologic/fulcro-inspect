(ns fulcro.inspect.client
  (:require [clojure.set :as set]
            [fulcro-css.css :as css]
            [fulcro.client :as fulcro]
            [fulcro.client.primitives :as fp]
            [fulcro.client.mutations :as fm]
            [fulcro.client.network :as f.network]
            [fulcro.inspect.ui.element-picker :as picker]
            [fulcro.inspect.remote.transit :as encode]
            [fulcro.inspect.ui.helpers :as ui.h]
            [fulcro.inspect.ui.dom-history-viewer :as dom-history]
            [goog.object :as gobj]
            [fulcro.client.localized-dom :as dom]))

(defonce started?* (atom false))
(defonce tools-app* (atom nil))
(defonce apps* (atom {}))

(def app-uuid-key :fulcro.inspect.core/app-uuid)

(defn post-message [type data]
  (.postMessage js/window #js {:fulcro-inspect-remote-message (encode/write {:type type :data data :timestamp (js/Date.)})} "*"))

(declare handle-devtool-message)

(defn event-data [event]
  (some-> event (gobj/getValueByKeys "data" "fulcro-inspect-devtool-message") encode/read))

(defn listen-local-messages []
  (.addEventListener js/window "message"
    (fn [event]
      (when (and (= (.-source event) js/window)
                 (gobj/getValueByKeys event "data" "fulcro-inspect-devtool-message"))
        (handle-devtool-message (event-data event))))
    false))

(defn app-uuid [reconciler]
  (some-> reconciler fp/app-state deref app-uuid-key))

(defn app-id [reconciler]
  (or (some-> reconciler fp/app-state deref :fulcro.inspect.core/app-id)
      (some-> reconciler fp/app-root ui.h/react-display-name)))

(defn inspect-network-init [network app]
  (some-> network :options ::app* (reset! app)))

(defn inspect-transact!
  ([tx]
   (post-message ::transact-client {::tx tx}))
  ([ref tx]
   (post-message ::transact-client {::tx-ref ref ::tx tx})))

(defn update-inspect-state [app-id state]
  (inspect-transact! [:fulcro.inspect.ui.data-history/history-id [app-uuid-key app-id]]
    [`(fulcro.inspect.ui.data-history/set-content ~state) :fulcro.inspect.ui.data-history/history]))

(defn inspect-app [{:keys [reconciler] :as app}]
  (let [state*   (some-> app :reconciler :config :state)
        app-uuid (random-uuid)]

    (inspect-network-init (-> app :networking :remote) app)

    (add-watch state* app-uuid
      #(update-inspect-state app-uuid %4))

    (swap! apps* assoc app-uuid app)
    (swap! state* assoc app-uuid-key app-uuid)

    (post-message ::init-app {app-uuid-key                app-uuid
                              :fulcro.inspect.core/app-id (app-id reconciler)
                              ::initial-state             @state*})

    app))

(defn inspect-tx [{:keys [reconciler] :as env} info]
  (if (fp/app-root reconciler)
    (let [tx     (-> (merge info (select-keys env [:old-state :new-state :ref :component]))
                     (update :component #(gobj/get (fp/react-type %) "displayName"))
                     (set/rename-keys {:ref :ident-ref}))
          app-id (app-uuid reconciler)]
      ; ensure app is initialized
      (if (-> reconciler fp/app-state deref :fulcro.inspect.core/app-uuid)
        (inspect-transact! [:fulcro.inspect.ui.transactions/tx-list-id [app-uuid-key app-id]]
          [`(fulcro.inspect.ui.transactions/add-tx ~tx) :fulcro.inspect.ui.transactions/tx-list])))))

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
  f.network/FulcroRemoteI
  (transmit [_ {::f.network/keys [edn ok-handler error-handler]}]
    (let [{::keys [transform-query transform-response transform-error app*]
           :or    {transform-query    (fn [_ x] x)
                   transform-response (fn [_ x] x)
                   transform-error    (fn [_ x] x)}} options
          req-id (random-uuid)
          env    {::request-id req-id
                  ::app        @app*}]
      (if-let [edn' (transform-query env edn)]
        (f.network/transmit network
          {::f.network/edn           edn'
           ::f.network/ok-handler    #(->> % (transform-response env) ok-handler)
           ::f.network/error-handler #(->> % (transform-error env) error-handler)})
        (ok-handler nil))))

  (abort [_ abort-id] (f.network/abort network abort-id)))

(defn transform-network-i [network options]
  (->TransformNetworkI network (assoc options ::app* (atom nil))))

(defn inspect-network
  ([remote network]
   (let [ts {::transform-query
             (fn [{::keys [request-id app]} edn]
               (let [app-id (app-uuid (:reconciler app))]
                 (inspect-transact! [:fulcro.inspect.ui.network/history-id [app-uuid-key app-id]]
                   [`(fulcro.inspect.ui.network/request-start ~{:fulcro.inspect.ui.network/remote      remote
                                                                :fulcro.inspect.ui.network/request-id  request-id
                                                                :fulcro.inspect.ui.network/request-edn edn})]))
               edn)

             ::transform-response
             (fn [{::keys [request-id app]} response]
               (let [app-id (app-uuid (:reconciler app))]
                 (inspect-transact! [:fulcro.inspect.ui.network/history-id [app-uuid-key app-id]]
                   [`(fulcro.inspect.ui.network/request-finish ~{:fulcro.inspect.ui.network/request-id   request-id
                                                                 :fulcro.inspect.ui.network/response-edn response})]))
               response)

             ::transform-error
             (fn [{::keys [request-id app]} error]
               (let [app-id (app-uuid (:reconciler app))]
                 (inspect-transact! [:fulcro.inspect.ui.network/history-id [app-uuid-key app-id]]
                   [`(fulcro.inspect.ui.network/request-finish ~{:fulcro.inspect.ui.network/request-id request-id
                                                                 :fulcro.inspect.ui.network/error      error})]))
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

(defn handle-devtool-message [{:keys [type data]}]
  (case type
    :fulcro.inspect.client/request-page-apps
    (doseq [{:keys [reconciler]} (vals @apps*)]
      (post-message ::init-app {app-uuid-key                (app-uuid reconciler)
                                :fulcro.inspect.core/app-id (app-id reconciler)
                                ::initial-state             @(fp/app-state reconciler)}))

    :fulcro.inspect.client/reset-app-state
    (let [{:keys                     [target-state]
           :fulcro.inspect.core/keys [app-uuid]} data]
      (if-let [{:keys [reconciler]} (get @apps* app-uuid)]
        (let [target-state (assoc target-state :fulcro.inspect.core/app-uuid app-uuid)]
          (some-> reconciler fp/app-state (reset! target-state))
          (js/setTimeout #(fp/force-root-render! reconciler) 10))
        (js/console.log "Reset app on invalid uuid" app-uuid)))

    :fulcro.inspect.client/transact
    (let [{:keys                     [tx tx-ref]
           :fulcro.inspect.core/keys [app-uuid]} data]
      (if-let [{:keys [reconciler]} (get @apps* app-uuid)]
        (if tx-ref
          (fp/transact! reconciler tx-ref tx)
          (fp/transact! reconciler tx))
        (js/console.log "Transact on invalid uuid" app-uuid)))

    :fulcro.inspect.client/pick-element
    (let [{:fulcro.inspect.core/keys [app-uuid]} data]
      (picker/pick-element
        {:fulcro.inspect.core/app-uuid
         app-uuid
         ::picker/on-pick
         (fn [comp]
           (if comp
             (let [details (picker/inspect-component comp)]
               (inspect-transact! [:fulcro.inspect.ui.element/panel-id [:fulcro.inspect.core/app-uuid app-uuid]]
                 [`(fulcro.inspect.ui.element/set-element ~details)]))
             (inspect-transact! [:fulcro.inspect.ui.element/panel-id [:fulcro.inspect.core/app-uuid app-uuid]]
               [`(fm/set-props {:ui/picking? false})])))}))

    :fulcro.inspect.client/show-dom-preview
    (let [{:fulcro.inspect.core/keys [app-uuid]} data
          app-state              (:state data)
          reconciler             (some-> @apps* (get app-uuid) :reconciler)
          app-root-class         (fp/react-type (fp/app-root reconciler))
          app-root-class-factory (fp/factory app-root-class)
          root-query             (fp/get-query app-root-class app-state)
          view-tree              (fp/db->tree root-query app-state app-state)
          data                   (assoc data :state (vary-meta view-tree assoc :render-fn app-root-class-factory))]
      (fp/transact! (:reconciler @tools-app*) [::dom-history/dom-viewer :singleton] [`(dom-history/show-dom-preview ~data)]))

    :fulcro.inspect.client/hide-dom-preview
    (fp/transact! (:reconciler @tools-app*) [::dom-history/dom-viewer :singleton] [`(dom-history/hide-dom-preview {})])

    :fulcro.inspect.client/network-request
    (let [{:keys                          [query]
           :fulcro.inspect.ui-parser/keys [msg-id]
           :fulcro.inspect.core/keys      [app-uuid]} data]
      (js/console.log "GOT NETWORK" data)
      (when-let [app (get @apps* app-uuid)]
        (js/console.log "Network tx" query app msg-id)
        (post-message :fulcro.inspect.client/message-response {:fulcro.inspect.ui-parser/msg-id msg-id
                                                               :fulcro.inspect.ui-parser/msg-response {:hello "World"}})))

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
