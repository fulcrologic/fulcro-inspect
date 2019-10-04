(ns fulcro.inspect.electron.renderer.main
  (:require
    [fulcro.client :as fulcro]
    [fulcro-css.css :as css]
    [fulcro.client.primitives :as fp]
    [fulcro.inspect.lib.local-storage :as storage]
    [fulcro.inspect.ui.multi-inspector :as multi-inspector]
    [fulcro.inspect.ui.element :as element]
    [fulcro.client.localized-dom :as dom]
    [fulcro.inspect.lib.websockets :as ws]))


(defonce socket (ws/websockets "http://localhost:8237" (fn [m] (js/console.log "Message " m))))

(comment
  (ws/start socket)
  (ws/push socket {:hello "world"}))

(fp/defsc GlobalRoot [this {:keys [ui/root]}]
  {:initial-state (fn [params] {:ui/root (fp/get-initial-state multi-inspector/MultiInspector params)})
   :query         [{:ui/root (fp/get-query multi-inspector/MultiInspector)}]
   :css           [[:body {:margin "0" :padding "0" :box-sizing "border-box"}]]
   :css-include   [multi-inspector/MultiInspector]}

  (dom/div
    (css/style-element this)
    (dom/div "Hello")
    (multi-inspector/multi-inspector root)))

(defonce ^:private global-inspector* (atom nil))
(defonce ^:private dom-node (atom nil))

(defn start-global-inspector! [options]
  (reset! global-inspector* (fulcro/new-fulcro-client :shared {:options options}))
  (reset! dom-node (js/document.createElement "div"))
  (js/document.body.appendChild @dom-node)
  (swap! global-inspector* fulcro/mount GlobalRoot @dom-node))

(defn global-inspector
  ([] @global-inspector*)
  ([options]
   (start-global-inspector! options)
   @global-inspector*))

(defn start []
  (js/console.log "start")
  (if @global-inspector*
    (swap! global-inspector* fulcro/mount GlobalRoot @dom-node)
    (start-global-inspector! {})))
