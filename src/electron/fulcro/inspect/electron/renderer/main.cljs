(ns fulcro.inspect.electron.renderer.main
  (:require
    [cljs.core.async :as async :refer [go <! put!]]
    [com.wsscode.common.async-cljs :refer [<?maybe]]
    [com.wsscode.pathom.core :as p]
    [com.wsscode.pathom.fulcro.network :as pfn]
    [fulcro-css.css :as css]
    [fulcro.client :as fulcro]
    [fulcro.client.localized-dom :as dom]
    [fulcro.client.mutations :as fm]
    [fulcro.client.primitives :as fp]
    [fulcro.i18n :as fulcro-i18n]
    [fulcro.inspect.lib.diff :as diff]
    [fulcro.inspect.lib.local-storage :as storage]
    [fulcro.inspect.lib.misc :as misc]
    [fulcro.inspect.lib.version :as version]
    [fulcro.inspect.remote.transit :as encode]
    [fulcro.inspect.ui-parser :as ui-parser]
    [fulcro.inspect.ui.data-history :as data-history]
    [fulcro.inspect.ui.db-explorer :as db-explorer]
    [fulcro.inspect.ui.data-watcher :as data-watcher]
    [fulcro.inspect.ui.element :as element]
    [fulcro.inspect.ui.i18n :as i18n]
    [fulcro.inspect.ui.index-explorer :as fiex]
    [fulcro.inspect.ui.inspector :as inspector]
    [fulcro.inspect.ui.multi-inspector :as multi-inspector]
    [fulcro.inspect.ui.multi-oge :as multi-oge]
    [fulcro.inspect.ui.network :as network]
    [fulcro.inspect.ui.transactions :as transactions]
    [goog.object :as gobj]
    [taoensso.encore :as enc]
    [fulcro.client.mutations :as m]))

(defonce electron (js/require "electron"))
(def ipcRenderer (gobj/get electron "ipcRenderer"))

(fp/defsc GlobalRoot [this {:ui/keys [root]}]
  {:initial-state (fn [params] {:ui/root (fp/get-initial-state multi-inspector/MultiInspector params)})
   :query         [{:ui/root (fp/get-query multi-inspector/MultiInspector)}]
   :css           [[:body {:margin "0" :padding "0" :box-sizing "border-box"}]]
   :css-include   [multi-inspector/MultiInspector]}
  (dom/div
    (css/style-element this)
    (multi-inspector/multi-inspector root)))

(defonce ^:private global-inspector* (atom nil))
(defonce ^:private dom-node (atom nil))

(def app-uuid-key :fulcro.inspect.core/app-uuid)

(def current-tab-id 42)

;; LANDMARK: This is how we talk back to the node server, which can send the websocket message
(defn post-message [port type data]
  (.send ipcRenderer "event" #js {:fulcro-inspect-devtool-message (encode/write {:type type :data data :timestamp (js/Date.)})
                                  :client-connection-id           (:fulcro.inspect.core/client-connection-id data)
                                  :tab-id                         current-tab-id}))

(defn event-data [event]
  (some-> event (gobj/get "fulcro-inspect-remote-message") encode/read))

(defn client-connection-id [event] (some-> event (gobj/get "client-id")))

(defn inc-id [id]
  (let [new-id (if-let [[_ prefix d] (re-find #"(.+?)(\d+)$" (str id))]
                 (str prefix (inc (js/parseInt d)))
                 (str id "-0"))]
    (cond
      (keyword? id) (keyword (subs new-id 1))
      (symbol? id) (symbol new-id)
      :else new-id)))

(defn inspector-app-names []
  (some->> @global-inspector* :reconciler fp/app-state deref ::inspector/id vals
    (mapv ::inspector/name) set))

(defn dedupe-name [name]
  (let [names-in-use (inspector-app-names)]
    (loop [new-name name]
      (if (contains? names-in-use new-name)
        (recur (inc-id new-name))
        new-name))))

(def DB_HISTORY_BUFFER_SIZE 100)

(defn db-index-add
  ([db app-uuid state] (db-index-add db app-uuid state (hash state)))
  ([db app-uuid state state-hash]
   (update db app-uuid #(misc/fixed-size-assoc DB_HISTORY_BUFFER_SIZE % state-hash state))))

(defonce last-disposed-app* (atom nil))

(defn start-app [{:fulcro.inspect.core/keys   [app-id app-uuid client-connection-id]
                  :fulcro.inspect.client/keys [initial-state state-hash remotes] :as data}]
  (let [inspector     @global-inspector*
        initial-state (assoc initial-state :fulcro.inspect.client/state-hash state-hash)
        new-inspector (-> (fp/get-initial-state inspector/Inspector initial-state)
                        (assoc ::inspector/id app-uuid)
                        (assoc :fulcro.inspect.core/client-connection-id client-connection-id)
                        (assoc :fulcro.inspect.core/app-id app-id)
                        (assoc ::inspector/name (dedupe-name app-id))
                        (assoc-in [::inspector/db-explorer ::db-explorer/id] [app-uuid-key app-uuid])
                        (assoc-in [::inspector/app-state ::data-history/history-id] [app-uuid-key app-uuid])
                        (assoc-in [::inspector/app-state ::data-history/watcher ::data-watcher/id] [app-uuid-key app-uuid])
                        (assoc-in [::inspector/app-state ::data-history/watcher ::data-watcher/watches]
                          (->> (storage/get [::data-watcher/watches app-id] [])
                            (mapv (fn [path]
                                    (fp/get-initial-state data-watcher/WatchPin
                                      {:path     path
                                       :expanded (storage/get [::data-watcher/watches-expanded app-id path] {})
                                       :content  (get-in initial-state path)})))))
                        (assoc-in [::inspector/app-state ::data-history/snapshots] (storage/tget [::data-history/snapshots app-id] []))
                        (assoc-in [::inspector/network ::network/history-id] [app-uuid-key app-uuid])
                        (assoc-in [::inspector/element ::element/panel-id] [app-uuid-key app-uuid])
                        (assoc-in [::inspector/i18n ::i18n/id] [app-uuid-key app-uuid])
                        (assoc-in [::inspector/i18n ::i18n/current-locale] (-> (get-in initial-state (-> initial-state ::fulcro-i18n/current-locale))
                                                                             ::fulcro-i18n/locale))
                        (assoc-in [::inspector/i18n ::i18n/locales] (->> initial-state ::fulcro-i18n/locale-by-id vals vec
                                                                      (mapv #(vector (::fulcro-i18n/locale %) (:ui/locale-name %)))))
                        (assoc-in [::inspector/transactions ::transactions/tx-list-id] [app-uuid-key app-uuid])
                        (assoc-in [::inspector/oge] (fp/get-initial-state multi-oge/OgeView {:app-uuid app-uuid
                                                                                             :remotes  remotes}))
                        (assoc-in [::inspector/index-explorer] (fp/get-initial-state fiex/IndexExplorer
                                                                 {:app-uuid app-uuid
                                                                  :remotes  remotes})))]

    (let [{::keys [db-hash-index]} (-> inspector :reconciler :config :shared)]
      (swap! db-hash-index db-index-add app-uuid (dissoc initial-state :fulcro.inspect.client/state-hash) state-hash))

    (fp/transact! (:reconciler inspector) [::multi-inspector/multi-inspector "main"]
      [`(multi-inspector/add-inspector ~new-inspector)])

    (when (= app-id @last-disposed-app*)
      (reset! last-disposed-app* nil)
      (fp/transact! (:reconciler inspector) [::multi-inspector/multi-inspector "main"]
        [`(multi-inspector/set-app {::inspector/id ~app-uuid})]))

    new-inspector))

(defn dispose-app [{:fulcro.inspect.core/keys [app-uuid]}]
  (let [{:keys [reconciler] :as inspector} @global-inspector*
        state         (fp/app-state reconciler)
        inspector-ref [::inspector/id app-uuid]
        app-id        (get (get-in @state inspector-ref) :fulcro.inspect.core/app-id)
        {::keys [db-hash-index]} (-> inspector :reconciler :config :shared)]

    (if (= (get-in @state [::multi-inspector/multi-inspector "main" ::multi-inspector/current-app])
          inspector-ref)
      (reset! last-disposed-app* app-id)
      (reset! last-disposed-app* nil))

    (swap! db-hash-index dissoc app-uuid)                   ; clear state history

    (fp/transact! reconciler [::multi-inspector/multi-inspector "main"]
      [`(multi-inspector/remove-inspector {::inspector/id ~app-uuid})])))

(defn tx-run [{:fulcro.inspect.client/keys [tx tx-ref]}]
  (let [{:keys [reconciler]} @global-inspector*]
    (if tx-ref
      (fp/transact! reconciler tx-ref tx)
      (fp/transact! reconciler tx))))

(defn reset-inspector []
  (-> @global-inspector* :reconciler fp/app-state (reset! (fp/tree->db GlobalRoot (fp/get-initial-state GlobalRoot {}) true))))

(defn update-client-db [{:fulcro.inspect.core/keys   [app-uuid]
                         :fulcro.inspect.client/keys [prev-state-hash state-delta state state-hash]}]
  (let [{::keys [db-hash-index]} (-> @global-inspector* :reconciler :config :shared)
        new-state (if state-delta
                    (if-let [old-state (get-in @db-hash-index [app-uuid prev-state-hash])]
                      (diff/patch old-state state-delta)
                      (js/console.error "Error patching state, no previous state available." state-hash))
                    state)]

    (swap! db-hash-index db-index-add app-uuid new-state state-hash)

    (if-let [current-locale (-> new-state ::fulcro-i18n/current-locale p/ident-value*)]
      (fp/transact! (:reconciler @global-inspector*)
        [::i18n/id [app-uuid-key app-uuid]]
        [`(fm/set-props ~{::i18n/current-locale current-locale})]))

    (let [state (assoc new-state :fulcro.inspect.client/state-hash state-hash)]
      (fp/transact! (:reconciler @global-inspector*)
        [::db-explorer/id [app-uuid-key app-uuid]]
        [`(db-explorer/set-current-state ~state) :current-state])
      (fp/transact! (:reconciler @global-inspector*)
        [::data-history/history-id [app-uuid-key app-uuid]]
        [`(data-history/set-content ~state)
         :current-state
         ::data-history/history]))))

(defn new-client-tx [{:fulcro.inspect.core/keys   [app-uuid]
                      :fulcro.inspect.client/keys [tx]}]
  (let [{::keys [db-hash-index]} (-> @global-inspector* :reconciler :config :shared)
        tx (assoc tx
             :fulcro.history/db-before (get-in @db-hash-index [app-uuid (:fulcro.history/db-before-hash tx)])
             :fulcro.history/db-after (get-in @db-hash-index [app-uuid (:fulcro.history/db-after-hash tx)]))]
    (fp/transact! (:reconciler @global-inspector*)
      [:fulcro.inspect.ui.transactions/tx-list-id [app-uuid-key app-uuid]]
      [`(fulcro.inspect.ui.transactions/add-tx ~tx) :fulcro.inspect.ui.transactions/tx-list])))

(defn set-active-app [{:fulcro.inspect.core/keys [app-uuid]}]
  (let [inspector @global-inspector*]
    (fp/transact! (:reconciler inspector) [::multi-inspector/multi-inspector "main"]
      [`(multi-inspector/set-app {::inspector/id ~app-uuid})])))

(defn notify-stale-app []
  (let [inspector @global-inspector*]
    (fp/transact! (:reconciler inspector) [::multi-inspector/multi-inspector "main"]
      [`(fm/set-props {::multi-inspector/client-stale? true})])))

(defn handle-remote-message [{:keys [port event responses*]}]
  (enc/when-let [{:keys [type data]} (event-data event)
                 client-id (client-connection-id event)]
    (let [data (assoc data ::port port :fulcro.inspect.core/client-connection-id client-id)]
      (case type
        :fulcro.inspect.client/init-app
        (start-app data)

        :fulcro.inspect.client/db-update
        (update-client-db data)

        :fulcro.inspect.client/new-client-transaction
        (new-client-tx data)

        :fulcro.inspect.client/transact-inspector
        (tx-run data)

        :fulcro.inspect.client/reset
        (reset-inspector)

        :fulcro.inspect.client/dispose-app
        (dispose-app data)

        :fulcro.inspect.client/set-active-app
        (set-active-app data)

        :fulcro.inspect.client/message-response
        (if-let [res-chan (get @responses* (::ui-parser/msg-id data))]
          (put! res-chan (::ui-parser/msg-response data)))

        :fulcro.inspect.client/client-version
        (let [client-version (:version data)]
          (if (= -1 (version/compare client-version version/last-inspect-version))
            (notify-stale-app)))

        (js/console.log "Unknown message" type)))))

(defonce message-handler-ch (async/chan (async/dropping-buffer 1024)))

(defn event-loop [app responses*]
  (.on ipcRenderer "event" (fn [_ msg]
                             (handle-remote-message {:event      msg
                                                     :responses* responses*}))))

(defn make-network [port* parser responses*]
  (pfn/fn-network
    (fn [this edn ok error]
      (go
        (try
          (ok (<! (parser {:send-message (fn [type data]
                                           (post-message @port* type data))
                           :responses*   responses*} edn)))
          (catch :default e
            (error e)))))
    false))

(defn start-global-inspector [options]
  (let [port*      (atom nil)
        responses* (atom {})
        app        (fulcro/new-fulcro-client
                     :started-callback
                     (fn [app]
                       (reset! port* (event-loop app responses*))
                       (post-message @port* :fulcro.inspect.client/check-client-version {}))

                     :shared
                     {::db-hash-index (atom {})}

                     :networking
                     (make-network port* (ui-parser/parser) responses*))
        node       (js/document.createElement "div")]
    (js/document.body.appendChild node)
    (reset! global-inspector* (fulcro/mount app GlobalRoot node))
    (reset! dom-node node)))

(defn global-inspector
  ([] @global-inspector*)
  ([options]
   (start-global-inspector options)
   @global-inspector*))

(defn start []
  (if @global-inspector*
    (fulcro/mount @global-inspector* GlobalRoot @dom-node)
    (start-global-inspector {})))

(start)
