(ns fulcro.inspect.chrome.devtool.main
  (:require [fulcro.client :as fulcro]
            [fulcro-css.css :as css]
            [goog.object :as gobj]
            [fulcro.client.primitives :as fp]
            [fulcro.inspect.lib.local-storage :as storage]
            [fulcro.inspect.ui.data-history :as data-history]
            [fulcro.inspect.ui.inspector :as inspector]
            [fulcro.inspect.ui.multi-inspector :as multi-inspector]
            [fulcro.inspect.ui.element :as element]
            [fulcro.inspect.ui.network :as network]
            [fulcro.inspect.ui.transactions :as transactions]
            [fulcro.client.localized-dom :as dom]
            [fulcro.inspect.remote.transit :as encode]
            [fulcro.inspect.helpers :as db.h]
            [fulcro.inspect.ui.helpers :as ui.h]))

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

(comment
  (-> @global-inspector* :reconciler fp/app-state deref
      (db.h/get-in-path [::data-history/history-id
                         [app-uuid-key "add-item-demo"]
                         ::data-history/watcher
                         :fulcro.inspect.ui.data-watcher/root-data
                         :fulcro.inspect.ui.data-viewer/content
                         :fulcro.inspect.ui.demos-cards/todo-app-id])

      ffirst
      type)

  (first *1)
  (uuid? *1))

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

(defn start-app [{:fulcro.inspect.core/keys   [app-id app-uuid]
                  :fulcro.inspect.remote/keys [initial-state]}]
  (let [inspector     @global-inspector*
        new-inspector (-> (fp/get-initial-state inspector/Inspector initial-state)
                          (assoc ::inspector/id app-uuid)
                          (assoc :fulcro.inspect.core/app-id app-id)
                          (assoc ::inspector/name (dedupe-name app-id))
                          (assoc-in [::inspector/app-state ::data-history/history-id] [app-uuid-key app-uuid])
                          (assoc-in [::inspector/app-state ::data-history/snapshots] (storage/tget [::data-history/snapshots app-id] []))
                          (assoc-in [::inspector/network ::network/history-id] [app-uuid-key app-uuid])
                          (assoc-in [::inspector/element ::element/panel-id] [app-uuid-key app-uuid])
                          (assoc-in [::inspector/transactions ::transactions/tx-list-id] [app-uuid-key app-uuid]))]

    (fp/transact! (:reconciler inspector) [::multi-inspector/multi-inspector "main"]
      [`(multi-inspector/add-inspector ~new-inspector)])

    new-inspector))

(defn tx-run [{:fulcro.inspect.remote/keys [tx tx-ref]}]
  (let [{:keys [reconciler]} @global-inspector*]
    (if tx-ref
      (fp/transact! reconciler tx-ref tx)
      (fp/transact! reconciler tx))))

(defn reset-inspector []
  (-> @global-inspector* :reconciler fp/app-state (reset! (fp/tree->db GlobalRoot (fp/get-initial-state GlobalRoot {}) true))))

(defn handle-loop-event [port event]
  (js/console.log "DEV EVENT" event)
  (when-let [{:keys [type data]} (event-data event)]
    (let [data (assoc data ::port port)]
      (case type
        :fulcro.inspect.remote/init-app
        (start-app data)

        :fulcro.inspect.remote/transact-client
        (tx-run data)

        :fulcro.inspect.remote/reset
        (reset-inspector)

        nil))))

(defn event-loop [app]
  (js/console.log "LISTEN TO PORT")
  (let [port (js/chrome.runtime.connect #js {:name "fulcro-inspect-devtool"})]
    (.addListener (.-onMessage port) #(handle-loop-event port %))

    (.postMessage port #js {:name   "init"
                            :tab-id current-tab-id})
    (post-message port :fulcro.inspect.client/request-page-apps {})))

(defn start-global-inspector [options]
  (let [app  (fulcro/new-fulcro-client
               :started-callback
               (fn [app] (event-loop app)))
        node (js/document.createElement "div")]
    (js/document.body.appendChild node)
    (fulcro/mount app GlobalRoot node)))

(defn global-inspector
  ([] @global-inspector*)
  ([options]
   (or @global-inspector*
       (reset! global-inspector* (start-global-inspector options)))))

(global-inspector {})
