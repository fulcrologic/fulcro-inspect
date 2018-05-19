(ns fulcro.inspect.chrome.devtool.main
  (:require [fulcro.client :as fulcro]
            [fulcro-css.css :as css]
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
            [fulcro.inspect.ui.helpers :as ui.h]))

(fp/defsc GlobalRoot [this {:keys [ui/root]}]
  {:initial-state (fn [params] {:ui/root (fp/get-initial-state multi-inspector/MultiInspector params)})
   :query         [{:ui/root (fp/get-query multi-inspector/MultiInspector)}]
   :css           [[:body {:margin "0" :padding "0" :box-sizing "border-box"}]]
   :css-include   [multi-inspector/MultiInspector]}

  (dom/div
    (css/style-element this)
    (multi-inspector/multi-inspector root)))

(defonce ^:private global-inspector* (atom nil))

(defn event-data [event] (encode/read (.-data event)))

(defn inc-id [id]
  (let [new-id (if-let [[_ prefix d] (re-find #"(.+?)(\d+)$" (str id))]
                 (str prefix (inc (js/parseInt d)))
                 (str id "-0"))]
    (cond
      (keyword? id) (keyword (subs new-id 1))
      (symbol? id) (symbol new-id)
      :else new-id)))

(defn inspector-app-ids []
  (some-> @global-inspector* :reconciler fp/app-state deref ::inspector/id))

(defn dedupe-id [id]
  (let [ids-in-use (inspector-app-ids)]
    (loop [new-id id]
      (if (contains? ids-in-use new-id)
        (recur (inc-id new-id))
        new-id))))

(defn start-app [{:fulcro.inspect.remote/keys [app-id initial-state]}]
  (let [inspector     @global-inspector*
        app-id        (dedupe-id app-id)
        new-inspector (-> (fp/get-initial-state inspector/Inspector initial-state)
                          (assoc ::inspector/id app-id)
                          ;(assoc ::inspector/target-app target-app)
                          (assoc-in [::inspector/app-state ::data-history/history-id] [::app-id app-id])
                          (assoc-in [::inspector/app-state ::data-history/snapshots] (storage/tget [::data-history/snapshots (ui.h/normalize-id app-id)] []))
                          (assoc-in [::inspector/network ::network/history-id] [::app-id app-id])
                          (assoc-in [::inspector/element ::element/panel-id] [::app-id app-id])
                          ;(assoc-in [::inspector/element ::element/target-reconciler] (:reconciler target-app))
                          (assoc-in [::inspector/transactions ::transactions/tx-list-id] [::app-id app-id]))]

    (fp/transact! (:reconciler inspector) [::multi-inspector/multi-inspector "main"]
      [`(multi-inspector/add-inspector ~new-inspector)])

    new-inspector))

(defn event-loop [app]
  (js/console.log "LISTEN TO PORT")
  (let [port (js/chrome.runtime.connect #js {:name "fulcro-inspect-devtools-background"})]
    (.addListener (.-onMessage port)
      (fn [event]
        (case (.-type event)
          "fulcro-inspect-app-start"
          (start-app (event-data event))

          nil)))
    (.postMessage port #js {:name  "init"
                            :tabId js/chrome.devtools.inspectedWindow.tabId})))

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
