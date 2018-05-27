(ns fulcro.inspect.chrome.devtool.main
  (:require [cljs.core.async :refer [go <! put!]]
            [com.wsscode.oge.core :as oge]
            [com.wsscode.pathom.fulcro.network :as pfn]
            [fulcro-css.css :as css]
            [fulcro.client :as fulcro]
            [fulcro.client.localized-dom :as dom]
            [fulcro.client.primitives :as fp]
            [fulcro.inspect.lib.local-storage :as storage]
            [fulcro.inspect.remote.transit :as encode]
            [fulcro.inspect.ui-parser :as ui-parser]
            [fulcro.inspect.ui.data-history :as data-history]
            [fulcro.inspect.ui.element :as element]
            [fulcro.inspect.ui.inspector :as inspector]
            [fulcro.inspect.ui.multi-inspector :as multi-inspector]
            [fulcro.inspect.ui.multi-oge :as multi-oge]
            [fulcro.inspect.ui.network :as network]
            [fulcro.inspect.ui.transactions :as transactions]
            [goog.object :as gobj]))

(fp/defsc GlobalRoot [this {:keys [ui/root]}]
  {:initial-state (fn [params] {:ui/root (fp/get-initial-state multi-inspector/MultiInspector params)})
   :query         [{:ui/root (fp/get-query multi-inspector/MultiInspector)}]
   :css           [[:body {:margin "0" :padding "0" :box-sizing "border-box"}]]
   :css-include   [multi-inspector/MultiInspector]}

  (dom/div
    (css/style-element this)
    (multi-inspector/multi-inspector root)))

(def app-uuid-key :fulcro.inspect.core/app-uuid)

(defonce global-inspector* (atom nil))

(def current-tab-id js/chrome.devtools.inspectedWindow.tabId)

(defn post-message [port type data]
  (.postMessage port #js {:fulcro-inspect-devtool-message (encode/write {:type type :data data :timestamp (js/Date.)})
                          :tab-id                         current-tab-id}))

(defn event-data [event]
  (some-> event (gobj/getValueByKeys "fulcro-inspect-remote-message") encode/read))

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

(defn db-index-add [db state]
  (let [{::keys [history] :as db'}
        (-> db
            (assoc (hash state) state)
            (update ::history conj (hash state)))]
    (if (> (count history) DB_HISTORY_BUFFER_SIZE)
      (-> db'
          (dissoc (first history))
          (update ::history #(vec (next %))))
      db')))

(defn start-app [{:fulcro.inspect.core/keys   [app-id app-uuid]
                  :fulcro.inspect.client/keys [initial-state remotes]}]
  (let [inspector     @global-inspector*
        new-inspector (-> (fp/get-initial-state inspector/Inspector initial-state)
                          (assoc ::inspector/id app-uuid)
                          (assoc :fulcro.inspect.core/app-id app-id)
                          (assoc ::inspector/name (dedupe-name app-id))
                          (assoc-in [::inspector/app-state ::data-history/history-id] [app-uuid-key app-uuid])
                          (assoc-in [::inspector/app-state ::data-history/snapshots] (storage/tget [::data-history/snapshots app-id] []))
                          (assoc-in [::inspector/network ::network/history-id] [app-uuid-key app-uuid])
                          (assoc-in [::inspector/element ::element/panel-id] [app-uuid-key app-uuid])
                          (assoc-in [::inspector/transactions ::transactions/tx-list-id] [app-uuid-key app-uuid])
                          (assoc-in [::inspector/oge] (fp/get-initial-state multi-oge/OgeView {:app-uuid app-uuid
                                                                                               :remotes  remotes})))]

    (let [{::keys [db-hash-index]} (-> inspector :reconciler :config :shared)]
      (swap! db-hash-index db-index-add initial-state))

    (fp/transact! (:reconciler inspector) [::multi-inspector/multi-inspector "main"]
      [`(multi-inspector/add-inspector ~new-inspector)])

    (doseq [r remotes]
      (oge/trigger-index-load (:reconciler @global-inspector*) [:oge/id [app-uuid-key app-uuid r]] r))

    new-inspector))

(defn tx-run [{:fulcro.inspect.client/keys [tx tx-ref]}]
  (let [{:keys [reconciler]} @global-inspector*]
    (if tx-ref
      (fp/transact! reconciler tx-ref tx)
      (fp/transact! reconciler tx))))

(defn reset-inspector []
  (-> @global-inspector* :reconciler fp/app-state (reset! (fp/tree->db GlobalRoot (fp/get-initial-state GlobalRoot {}) true))))

(defn update-client-db [{:fulcro.inspect.core/keys   [app-uuid]
                         :fulcro.inspect.client/keys [state]}]
  (let [{::keys [db-hash-index]} (-> @global-inspector* :reconciler :config :shared)]
    (swap! db-hash-index db-index-add state))

  (fp/transact! (:reconciler @global-inspector*)
    [:fulcro.inspect.ui.data-history/history-id [app-uuid-key app-uuid]]
    [`(fulcro.inspect.ui.data-history/set-content ~state) :fulcro.inspect.ui.data-history/history]))

(defn new-client-tx [{:fulcro.inspect.core/keys   [app-uuid]
                      :fulcro.inspect.client/keys [tx]}]
  (let [{::keys [db-hash-index]} (-> @global-inspector* :reconciler :config :shared)
        tx (assoc tx
             :fulcro.history/db-before (get @db-hash-index (:fulcro.history/db-before-hash tx))
             :fulcro.history/db-after (get @db-hash-index (:fulcro.history/db-after-hash tx)))]
    (fp/transact! (:reconciler @global-inspector*)
      [:fulcro.inspect.ui.transactions/tx-list-id [app-uuid-key app-uuid]]
      [`(fulcro.inspect.ui.transactions/add-tx ~tx) :fulcro.inspect.ui.transactions/tx-list])))

(defn handle-remote-message [{:keys [port event responses*]}]
  (when-let [{:keys [type data]} (event-data event)]
    (let [data (assoc data ::port port)]
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

        :fulcro.inspect.client/message-response
        (if-let [res-chan (get @responses* (::ui-parser/msg-id data))]
          (put! res-chan (::ui-parser/msg-response data)))

        nil))))

(defn event-loop [app responses*]
  (let [port (js/chrome.runtime.connect #js {:name "fulcro-inspect-devtool"})]
    (.addListener (.-onMessage port) #(handle-remote-message {:port       port
                                                              :event      %
                                                              :responses* responses*}))

    (.postMessage port #js {:name "init" :tab-id current-tab-id})
    (post-message port :fulcro.inspect.client/request-page-apps {})

    port))

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
                       (reset! port* (event-loop app responses*)))

                     :shared
                     {::db-hash-index (atom {::history []})}

                     :networking
                     (make-network port* ui-parser/parser responses*))
        node       (js/document.createElement "div")]
    (js/document.body.appendChild node)
    (fulcro/mount app GlobalRoot node)))

(defn global-inspector
  ([] @global-inspector*)
  ([options]
   (or @global-inspector*
       (reset! global-inspector* (start-global-inspector options)))))

(global-inspector {})
