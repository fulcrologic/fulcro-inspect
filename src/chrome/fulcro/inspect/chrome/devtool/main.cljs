(ns fulcro.inspect.chrome.devtool.main
  (:require
    [cljs.core.async :as async :refer [<! go put!]]
    [com.fulcrologic.fulcro-css.css :as css]
    [com.fulcrologic.fulcro-css.css-injection :as cssi]
    [com.fulcrologic.fulcro-css.localized-dom :as dom]
    [com.fulcrologic.fulcro-i18n.i18n :as fulcro-i18n]
    [com.fulcrologic.fulcro.algorithms.normalize :as fnorm]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.application :as fulcro]
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.fulcro.components :as fp]
    [com.fulcrologic.fulcro.mutations :as fm]
    [com.fulcrologic.fulcro.networking.mock-server-remote :as mock-net]
    [com.wsscode.common.async-cljs :refer [<?maybe]]
    [com.wsscode.pathom.core :as p]
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
    [fulcro.inspect.ui.transactions :as transactions]
    [goog.functions :refer [debounce]]
    [goog.object :as gobj]
    [taoensso.encore :as enc]
    [taoensso.timbre :as log]))

(declare fill-last-entry!)

(fp/defsc GlobalRoot [this {:keys [ui/root]}]
  {:initial-state (fn [params] {:ui/root
                                (-> (fp/get-initial-state multi-inspector/MultiInspector params)
                                  (assoc-in [::multi-inspector/settings :ui/hide-websocket?] true))})
   :query         [{:ui/root (fp/get-query multi-inspector/MultiInspector)}]
   :css           [[:html {:overflow "hidden"}]
                   [:body {:margin "0" :padding "0" :box-sizing "border-box"}]]
   :css-include   [multi-inspector/MultiInspector]}
  (dom/div
    (cssi/style-element {:component this})
    (multi-inspector/multi-inspector root)))

(def app-uuid-key :fulcro.inspect.core/app-uuid)

(defonce global-inspector* (atom nil))

(def current-tab-id js/chrome.devtools.inspectedWindow.tabId)

(defn post-message [port type data]
  (log/info "Posting message" type data)
  (.postMessage port #js {:fulcro-inspect-devtool-message (encode/write {:type type :data data :timestamp (js/Date.)})
                          :tab-id                         current-tab-id}))

(defn event-data [event]
  (some-> event (gobj/get "fulcro-inspect-remote-message") encode/read))

(defn inc-id [id]
  (let [new-id (if-let [[_ prefix d] (re-find #"(.+?)(\d+)$" (str id))]
                 (str prefix (inc (js/parseInt d)))
                 (str id "-0"))]
    (cond
      (keyword? id) (keyword (subs new-id 1))
      (symbol? id) (symbol new-id)
      :else new-id)))

(defn inspector-app-names []
  (some->> @global-inspector* (app/current-state) (::inspector/id) (vals)
    (mapv ::inspector/name) set))

(defn dedupe-name [name]
  (let [names-in-use (inspector-app-names)]
    (loop [new-name name]
      (if (contains? names-in-use new-name)
        (recur (inc-id new-name))
        new-name))))

(defonce last-disposed-app* (atom nil))

(defn start-app [{:fulcro.inspect.core/keys   [app-id app-uuid]
                  :fulcro.inspect.client/keys [initial-history-step remotes]}]
  (let [inspector     @global-inspector*
        {initial-state :value} initial-history-step
        new-inspector (-> (fp/get-initial-state inspector/Inspector initial-state)
                        (assoc ::inspector/id app-uuid)
                        (assoc :fulcro.inspect.core/app-id app-id)
                        (assoc ::inspector/name (dedupe-name app-id))
                        (assoc-in [::inspector/settings :ui/hide-websocket?] true)
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
                                                                                             :remotes  remotes})))]

    (hist/record-history-step! inspector app-uuid initial-history-step)
    (fill-last-entry!)

    (fp/transact! inspector [(multi-inspector/add-inspector new-inspector)]
      {:ref [::multi-inspector/multi-inspector "main"]})

    (when (= app-id @last-disposed-app*)
      (reset! last-disposed-app* nil)
      (fp/transact! inspector
        [(multi-inspector/set-app {::inspector/id app-uuid})]
        {:ref [::multi-inspector/multi-inspector "main"]}))

    (fp/transact! inspector
      [(db-explorer/set-current-state initial-history-step) :current-state]
      {:ref [::db-explorer/id [app-uuid-key app-uuid]]})
    (fp/transact! inspector
      [(data-history/set-content initial-history-step) ::data-history/history]
      {:ref [::data-history/history-id [app-uuid-key app-uuid]]})

    new-inspector))

(defn dispose-app [{:fulcro.inspect.core/keys [app-uuid]}]
  (let [app           @global-inspector*
        state         (app/current-state app)
        inspector-ref [::inspector/id app-uuid]
        app-id        (get (get-in state inspector-ref) :fulcro.inspect.core/app-id)
        {::keys [db-hash-index]} (fp/shared app)]

    (if (= (get-in @state [::multi-inspector/multi-inspector "main" ::multi-inspector/current-app])
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
  (-> @global-inspector* ::app/state-atom (reset! (fnorm/tree->db GlobalRoot (fp/get-initial-state GlobalRoot {}) true))))

(defn- -fill-last-entry!
  []
  (enc/if-let [app       @global-inspector*
               state-map (log/spy :info (app/current-state app))
               app-uuid  (log/spy :info (h/current-app-uuid state-map))
               state-id  (log/spy :info (hist/latest-state-id app app-uuid))]
    (fp/transact! app [(hist/remote-fetch-history-step {:id state-id})])
    (log/error "Something was nil")))

(def fill-last-entry!
  "Request the full state for the currently-selected application"
  (debounce -fill-last-entry! 250))

(defn update-client-db [{:fulcro.inspect.core/keys   [app-uuid]
                         :fulcro.inspect.client/keys [state-id]}]
  (let [step {:id state-id}]
    (hist/record-history-step! @global-inspector* app-uuid step)

    (fill-last-entry!)

    (fp/transact! @global-inspector*
      [(db-explorer/set-current-state step) :current-state]
      {:ref [::db-explorer/id [app-uuid-key app-uuid]]})
    (fp/transact! @global-inspector*
      [(data-history/set-content step) ::data-history/history]
      {:ref [::data-history/history-id [app-uuid-key app-uuid]]})))

(defn new-client-tx [{:fulcro.inspect.core/keys   [app-uuid]
                      :fulcro.inspect.client/keys [tx]}]
  (let [{:fulcro.history/keys [db-before-id
                               db-after-id]} tx
        inspector @global-inspector*
        tx        (assoc tx
                    :fulcro.history/db-before (hist/history-step inspector app-uuid db-before-id)
                    :fulcro.history/db-after (hist/history-step inspector app-uuid db-after-id))]
    (fp/transact! @global-inspector*
      [`(fulcro.inspect.ui.transactions/add-tx ~tx) :fulcro.inspect.ui.transactions/tx-list]
      {:ref [:fulcro.inspect.ui.transactions/tx-list-id [app-uuid-key app-uuid]]})))

(defn set-active-app [{:fulcro.inspect.core/keys [app-uuid]}]
  (let [inspector @global-inspector*]
    (fp/transact! inspector [(multi-inspector/set-app {::inspector/id app-uuid})]
      {:ref [::multi-inspector/multi-inspector "main"]})))

(defn notify-stale-app []
  (let [inspector @global-inspector*]
    (fp/transact! inspector [(fm/set-props {::multi-inspector/client-stale? true})]
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
    (app/force-root-render! inspector)))

;; LANDMARK: This is where incoming messages from the app are handled
(defn handle-remote-message [{:keys [port event responses*] :as message}]
  (when-let [{:keys [type data]} (log/spy :info (event-data event))]
    (let [data (assoc data ::port port)]
      (case type
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

        :fulcro.inspect.client/message-response
        (if-let [res-chan (get @responses* (::ui-parser/msg-id data))]
          (put! res-chan (::ui-parser/msg-response data)))

        :fulcro.inspect.client/client-version
        (let [client-version (:version data)]
          (if (= -1 (version/compare client-version version/last-inspect-version))
            (notify-stale-app)))

        (log/debug "Unknown message" type)))))

(defonce message-handler-ch (async/chan (async/dropping-buffer 1024)))

(defn event-loop [app responses*]
  (let [port (js/chrome.runtime.connect #js {:name "fulcro-inspect-devtool"})]
    (.addListener (.-onMessage port)
      (fn [msg]
        (put! message-handler-ch
          {:port       port
           :event      msg
           :responses* responses*})))
    (go
      (loop []
        (when-let [msg (<! message-handler-ch)]
          (<?maybe (handle-remote-message msg))
          (recur))))

    (.postMessage port #js {:name "init" :tab-id current-tab-id})
    (post-message port :fulcro.inspect.client/request-page-apps {})

    port))

(defn respond-locally! [responses* type data]
  (if-let [res-chan (get @responses* (::ui-parser/msg-id data))]
    (put! res-chan (::ui-parser/msg-response data))
    (log/error "Failed to respond locally to message:" type "with data:" data)))

(defn ?handle-local-message [responses* type data]
  (case type
    :fulcro.inspect.client/load-settings
    (let [settings (into {}
                     (remove (comp #{::not-found} second))
                     (for [k (:query data)]
                       [k (storage/get k ::not-found)]))]
      (respond-locally! responses* type
        {::ui-parser/msg-response settings
         ::ui-parser/msg-id       (::ui-parser/msg-id data)})
      :ok)
    :fulcro.inspect.client/save-settings
    (do
      (doseq [[k v] data]
        (log/trace "Saving setting:" k "=>" v)
        (storage/set! k v))
      :ok)
    #_else nil))

(defn make-network [port* parser responses*]
  (mock-net/mock-http-server
    {:parser (fn [edn]
               (log/info edn)
               (go
                 (log/spy :info
                   (async/<!
                     (parser
                       {:send-message (fn [type data]
                                        (or
                                          (?handle-local-message responses* type data)
                                          (post-message @port* type data)))
                        :responses*   responses*}
                       edn)))))}))

(defn start-global-inspector [options]
  (let [port*      (atom nil)
        responses* (atom {})
        app        (app/fulcro-app
                     {:props-middleware (comp/wrap-update-extra-props
                                          (fn [cls extra-props]
                                            (merge extra-props (css/get-classnames cls))))

                      :shared
                      {::hist/db-hash-index (atom {})}

                      :remotes          {:remote
                                         (make-network port* (ui-parser/parser) responses*)}})
        node       (js/document.createElement "div")]
    (js/document.body.appendChild node)
    ;; Sending a message of any kind will wake up the service worker, otherwise our comms won't succeed because
    ;; it will register for our initial comms AFTER we've sent them.
    ;; TASK: We must handle the service worker going away gracefully...not sure how to do that yet.
    (js/chrome.runtime.sendMessage #js {:ping true}
      (fn []
        (reset! port* (event-loop app responses*))
        (post-message @port* :fulcro.inspect.client/check-client-version {})
        (settings/load-settings app)))
    (app/mount! app GlobalRoot node)
    app))

(defn global-inspector
  ([] @global-inspector*)
  ([options]
   (or @global-inspector*
     (reset! global-inspector* (start-global-inspector options)))))

(global-inspector {})
