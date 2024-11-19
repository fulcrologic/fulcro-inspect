(ns fulcro.inspect.electron.renderer.main
  (:require
    [cljs.core.async :refer [<! go put!]]
    [com.fulcrologic.fulcro-css.css :as css]
    [com.fulcrologic.fulcro-css.css-injection :as cssi]
    [com.fulcrologic.fulcro-css.localized-dom :as dom]
    [com.fulcrologic.fulcro-i18n.i18n :as fulcro-i18n]
    [com.fulcrologic.fulcro.algorithms.normalize :as fnorm]
    [com.fulcrologic.fulcro.algorithms.tx-processing :as txn]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.application :as fulcro]
    [com.fulcrologic.fulcro.components :as fp]
    [com.fulcrologic.fulcro.mutations :as fm]
    [com.fulcrologic.statecharts.integration.fulcro :as scf]
    [com.wsscode.common.async-cljs :refer [<?maybe]]
    [edn-query-language.core :as eql]
    [fulcro.inspect.helpers :as h]
    [fulcro.inspect.lib.diff :as diff]
    [fulcro.inspect.lib.history :as hist]
    [fulcro.inspect.lib.local-storage :as storage]
    [fulcro.inspect.lib.version :as version]
    [fulcro.inspect.remote.transit :as encode]
    [fulcro.inspect.ui-parser :as ui-parser]
    [fulcro.inspect.ui.data-history :as data-history]
    [fulcro.inspect.ui.data-watcher :as data-watcher]
    [fulcro.inspect.ui.db-explorer :as db-explorer]
    [fulcro.inspect.ui.element :as element]
    [fulcro.inspect.ui.i18n :as i18n]
    [fulcro.inspect.ui.inspector :as inspector]
    [fulcro.inspect.ui.multi-inspector :as multi-inspector]
    [fulcro.inspect.ui.multi-oge :as multi-oge]
    [fulcro.inspect.ui.network :as network]
    [fulcro.inspect.ui.settings :as settings]
    [fulcro.inspect.ui.statecharts :as statecharts]
    [fulcro.inspect.ui.transactions :as transactions]
    [goog.functions :refer [debounce]]
    [goog.object :as gobj]
    [taoensso.encore :as enc]
    [taoensso.timbre :as log]))

(declare fill-last-entry!)

(def ipcRenderer js/window.ipcRenderer)

(fp/defsc GlobalRoot [this {:ui/keys [root]}]
  {:initial-state (fn [params] {:ui/root (fp/get-initial-state multi-inspector/MultiInspector params)})
   :query         [{:ui/root (fp/get-query multi-inspector/MultiInspector)}]
   :css           [[:body {:margin "0" :padding "0" :box-sizing "border-box"}]]
   :css-include   [multi-inspector/MultiInspector]}
  (dom/div
    (cssi/style-element {:component this})
    (multi-inspector/multi-inspector root)))

(defonce ^:private global-inspector* (atom nil))
(defonce ^:private dom-node (atom nil))

(def app-uuid-key :fulcro.inspect.core/app-uuid)

(def current-tab-id 42)

;; LANDMARK: This is how we talk back to the node server, which can send the websocket message
(defn post-message [type data]
  (log/info "render -> client" type)
  (log/info "method" (js/Object.getOwnPropertyNames ipcRenderer))
  (.send ipcRenderer "event"
    #js {:fulcro-inspect-devtool-message (encode/write {:type type :data data :timestamp (js/Date.)})
         :client-connection-id           (encode/write (:fulcro.inspect.core/client-connection-id data))
         :app-uuid                       (encode/write (:fulcro.inspect.core/app-uuid data))
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
  (some->> @global-inspector* app/current-state ::inspector/id vals
    (mapv ::inspector/name) set))

(defn dedupe-name [name]
  (let [names-in-use (inspector-app-names)]
    (loop [new-name name]
      (if (contains? names-in-use new-name)
        (recur (inc-id new-name))
        new-name))))

(defonce last-disposed-app* (atom nil))

(defn start-app [{:fulcro.inspect.core/keys   [app-id app-uuid client-connection-id]
                  :fulcro.inspect.client/keys [initial-history-step remotes]}]
  (let [app           @global-inspector*
        {initial-state :value} initial-history-step
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
                        (assoc-in [::inspector/transactions ::transactions/tx-list-id] [app-uuid-key app-uuid])
                        (assoc ::inspector/statecharts {::statecharts/id [app-uuid-key app-uuid]})
                        (assoc-in [::inspector/oge] (fp/get-initial-state multi-oge/OgeView {:app-uuid app-uuid
                                                                                             :remotes  remotes})))]

    (hist/record-history-step! app app-uuid initial-history-step)

    (fp/transact! app
      [(multi-inspector/add-inspector new-inspector)]
      {:ref [::multi-inspector/multi-inspector "main"]})

    (when (= app-id @last-disposed-app*)
      (reset! last-disposed-app* nil)
      (fp/transact! app [(multi-inspector/set-app {::inspector/id app-uuid})]
        {:ref [::multi-inspector/multi-inspector "main"]}))

    (fp/transact! app
      [(db-explorer/set-current-state initial-history-step) :current-state]
      {:ref [::db-explorer/id [app-uuid-key app-uuid]]})
    (fp/transact! app
      [(data-history/set-content initial-history-step) ::data-history/history]
      {:ref [::data-history/history-id [app-uuid-key app-uuid]]})

    new-inspector))

(defn dispose-app [{:fulcro.inspect.core/keys [app-uuid]}]
  (let [app           @global-inspector*
        state         (app/current-state app)
        inspector-ref [::inspector/id app-uuid]
        app-id        (get (get-in state inspector-ref) :fulcro.inspect.core/app-id)]

    (if (= (get-in state [::multi-inspector/multi-inspector "main" ::multi-inspector/current-app])
          inspector-ref)
      (reset! last-disposed-app* app-id)
      (reset! last-disposed-app* nil))

    (hist/clear-history! app app-uuid)

    (fp/transact! app [(multi-inspector/remove-inspector {::inspector/id app-uuid})]
      {:ref [::multi-inspector/multi-inspector "main"]})))

(defn tx-run [{:fulcro.inspect.client/keys [tx tx-ref]}]
  (let [app @global-inspector*]
    (if tx-ref
      (fp/transact! app tx {:ref tx-ref})
      (fp/transact! app tx))))

(defn reset-inspector []
  (-> @global-inspector*
    ::app/state-atom
    (reset! (fnorm/tree->db GlobalRoot (fp/get-initial-state GlobalRoot {}) true))))

(defn- -fill-last-entry!
  []
  (enc/if-let [app       @global-inspector*
               state-map (app/current-state app)
               app-uuid  (h/current-app-uuid state-map)
               state-id  (hist/latest-state-id app app-uuid)]
    (fp/transact! app [(hist/remote-fetch-history-step {:id state-id})])
    (js/console.error "Something was nil")))

(def fill-last-entry!
  "Request the full state for the currently-selected application"
  (debounce -fill-last-entry! 250))

(defn update-client-db [{:fulcro.inspect.core/keys   [app-uuid]
                         :fulcro.inspect.client/keys [state-id]}]
  (let [step {:id state-id}
        app  @global-inspector*]
    (hist/record-history-step! app app-uuid step)

    (fill-last-entry!)

    (fp/transact! app [(db-explorer/set-current-state step)] {:ref [::db-explorer/id [app-uuid-key app-uuid]]})
    (fp/transact! app [(data-history/set-content step)] {:ref [::data-history/history-id [app-uuid-key app-uuid]]})))

(defn new-client-tx [{:fulcro.inspect.core/keys   [app-uuid]
                      :fulcro.inspect.client/keys [tx]}]
  (let [{:fulcro.history/keys [db-before-id
                               db-after-id]} tx
        inspector @global-inspector*
        tx        (assoc tx
                    :fulcro.history/db-before (hist/history-step inspector app-uuid db-before-id)
                    :fulcro.history/db-after (hist/history-step inspector app-uuid db-after-id))]
    (fp/transact! inspector
      [(fulcro.inspect.ui.transactions/add-tx tx)]
      {:ref [:fulcro.inspect.ui.transactions/tx-list-id [app-uuid-key app-uuid]]})))

(defn set-active-app [{:fulcro.inspect.core/keys [app-uuid]}]
  (let [inspector @global-inspector*]
    (fp/transact! inspector [(multi-inspector/set-app {::inspector/id app-uuid})]
      {:ref [::multi-inspector/multi-inspector "main"]})))

(defn notify-stale-app []
  (let [inspector @global-inspector*]
    (fp/transact! inspector [`(fm/set-props {::multi-inspector/client-stale? true})]
      {:ref [::multi-inspector/multi-inspector "main"]})))

(defn fill-history-entry
  "Called in response to the client sending us the real state for a given state id, at which time we update
   our copy of the history with the new value"
  [{:fulcro.inspect.core/keys   [app-uuid state-id]
    :fulcro.inspect.client/keys [diff based-on state]}]
  (let [inspector @global-inspector*
        state     (if state
                    state
                    (let [base-state (hist/state-map-for-id inspector app-uuid based-on)]
                      (diff/patch base-state diff)))]
    (hist/record-history-step! inspector app-uuid {:id state-id :value state})
    (app/force-root-render! inspector)
    true))

;; LANDMARK: incoming electron app messages
(defn handle-remote-message [_ event]
  (log/info "Renderer receieved message " event)
  (enc/when-let [{:keys [type data]} (event-data event)
                 client-id (client-connection-id event)]
    (let [data (assoc data :fulcro.inspect.core/client-connection-id client-id)]
      (case (log/spy :info type)
        :fulcro.inspect.client/init-app
        (start-app data)

        :fulcro.inspect.client/db-changed!
        (update-client-db data)

        :fulcro.inspect.client/new-client-transaction
        (new-client-tx data)

        :fulcro.inspect.client/history-entry
        (fill-history-entry data)

        :fulcro.inspect.client/transact-inspector
        (tx-run data)

        :fulcro.inspect.client/reset
        (reset-inspector)

        :fulcro.inspect.client/dispose-app
        (dispose-app data)

        :fulcro.inspect.client/set-active-app
        (set-active-app data)

        :fulcro.inspect.client/client-version
        (let [client-version (:version data)]
          (if (= -1 (version/compare client-version version/last-inspect-version))
            (notify-stale-app)))

        :fulcro.inspect.client/console-log
        (let [{:keys [log log-js warn error]} data]
          (cond
            log (js/console.log log)
            log-js (js/console.log (clj->js log-js))
            warn (js/console.warn warn)
            error (js/console.error error))
          true)

        (js/console.log "Unknown remote message:" type)))))

(defn handle-local-message [{:keys [responses*]} event]
  (when-let [{:keys [type data]} (event-data event)]
    (case type
      :fulcro.inspect.client/message-response
      (when-let [res-chan (get @responses* (::ui-parser/msg-id data))]
        (put! res-chan (::ui-parser/msg-response data)))

      :fulcro.inspect.client/toggle-settings
      (fp/transact! @global-inspector*
        [(multi-inspector/toggle-settings data)]
        {:ref [::multi-inspector/multi-inspector "main"]})

      (js/console.warn "Unknown local message:" type))))

(defn event-loop! [_app responses*]
  (.on ipcRenderer "event"
    (fn [event]
      (or
        (handle-remote-message {:responses* responses*} event)
        (handle-local-message {:responses* responses*} event)))))

(defn make-network [parser responses*]
  (let [parser-env {:send-message post-message
                    :responses*   responses*}]
    {:transmit! (fn transmit! [_ {:keys [::txn/ast ::txn/result-handler ::txn/update-handler]}]
                  (go
                    (try
                      (let [edn  (eql/ast->query ast)
                            body (<! (parser parser-env edn))]
                        (result-handler {:status-code 200 :body body}))
                      (catch :default e
                        (log/error e "Handler failed")
                        (result-handler {:status-code 500 :body {}})))))}))

(defn start-global-inspector [_options]
  (let [responses* (atom {})
        app        (app/fulcro-app {:client-did-mount
                                    (fn [app]
                                      (event-loop! app responses*)
                                      (settings/load-settings app))

                                    :props-middleware (fp/wrap-update-extra-props
                                                         (fn [cls extra-props]
                                                           (merge extra-props (css/get-classnames cls))))


                                    :shared
                                    {::hist/db-hash-index               (atom {})
                                     :fulcro.inspect.renderer/electron? true}

                                    :remotes          {:remote
                                                       (make-network (ui-parser/parser) responses*)}})
        node       (js/document.createElement "div")]
    (js/document.body.appendChild node)
    (reset! global-inspector* app)
    (app/mount! app GlobalRoot node)
    (scf/install-fulcro-statecharts! app)
    (reset! dom-node node)))

(defn global-inspector
  ([] @global-inspector*)
  ([options]
   (start-global-inspector options)
   @global-inspector*))

(defn start []
  (if @global-inspector*
    (app/mount! @global-inspector* GlobalRoot @dom-node)
    (start-global-inspector {})))

(start)
