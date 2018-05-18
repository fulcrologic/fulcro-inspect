(ns fulcro.inspect.chrome.devtool.main
  (:require [fulcro.client :as fulcro]
            [fulcro-css.css :as css]
            [fulcro.client.primitives :as fp]
            [fulcro.inspect.lib.local-storage :as storage]
            [fulcro.inspect.ui.multi-inspector :as multi-inspector]
            [fulcro.inspect.ui.element :as element]
            [fulcro.client.localized-dom :as dom]))

(fp/defsc GlobalRoot [this {:keys [ui/root]}]
  {:initial-state (fn [params] {:ui/root (fp/get-initial-state multi-inspector/MultiInspector params)})
   :query         [{:ui/root (fp/get-query multi-inspector/MultiInspector)}]
   :css           [[:body {:margin "0" :padding "0" :box-sizing "border-box"}]]
   :css-include   [multi-inspector/MultiInspector]}

  (dom/div
    (css/style-element this)
    (multi-inspector/multi-inspector root)))

(defonce ^:private global-inspector* (atom nil))

(defn event-loop []
  (js/console.log "LISTEN TO PORT")
  (let [port (js/chrome.runtime.connect #js {:name "fulcro-inspect-devtools-background"})]
    (.addListener (.-onMessage port)
      (fn [event]
        (js/console.log "DEVTOOLS GOT EVENT" event)))
    (.postMessage port #js {:name  "init"
                            :tabId js/chrome.devtools.inspectedWindow.tabId})))

(defn start-global-inspector [options]
  (event-loop)
  (let [app  (fulcro/new-fulcro-client :shared {:options options})
        node (js/document.createElement "div")]
    (js/document.body.appendChild node)
    (fulcro/mount app GlobalRoot node)))

(defn global-inspector
  ([] @global-inspector*)
  ([options]
   (or @global-inspector*
       (reset! global-inspector* (start-global-inspector options)))))

(global-inspector {})
