(ns fulcro.inspect.ui.element
  (:require
    [clojure.data :as data]
    [clojure.string :as str]
    [fulcro-css.css :as css]
    [fulcro.client.core :as fulcro :refer-macros [defsc]]
    [fulcro.client.mutations :as mutations :refer-macros [defmutation]]
    [fulcro.inspect.helpers :as h]
    [fulcro.inspect.ui.core :as ui]
    [fulcro.inspect.ui.data-viewer :as data-viewer]
    [goog.object :as gobj]
    [om.dom :as dom]
    [om.next :as om :refer [get-query]]))

(defn pick-element [this]
  (let [reconciler      (om/get-reconciler this)
        elements        (some-> reconciler
                          :config :indexer
                          :indexes deref
                          :class->components vals
                          (->> (apply concat)
                               (keep #(js/ReactDOM.findDOMNode %))))
        clear           (atom nil)
        click-handler   (fn [e]
                          (.stopPropagation e)
                          (.preventDefault e)
                          (let [target (.-target e)]
                            (@clear)
                            (js/console.log "click" target)))
        enter-handler   (fn [e]
                          (let [target (.-target e)]

                            (js/console.log "enter" target)))
        leave-handler   (fn [e]
                          (let [target (.-target e)]

                            (js/console.log "left" target)))
        clear-events    (fn []
                          (js/console.log "clear things")
                          (doseq [e elements]
                            (.removeEventListener e "click" click-handler)
                            (.removeEventListener e "mouseenter" enter-handler)
                            (.removeEventListener e "mouseleave" leave-handler)))
        disable-handler (fn ch [e]
                          (clear-events)
                          (js/removeEventListener "click" ch))]

    (reset! clear clear-events)

    (doseq [e elements]
      (.addEventListener e "click" click-handler)
      (.addEventListener e "mouseenter" enter-handler)
      (.addEventListener e "mouseleave" leave-handler))

    (js/setTimeout
      #(js/addEventListener "click" disable-handler)
      10)))

(om/defui ^:once Panel
  static fulcro/InitialAppState
  (initial-state [this _]
    {::panel-id (random-uuid)})

  static om/Ident
  (ident [_ props] [::panel-id (::panel-id props)])

  static om/IQuery
  (query [_] [::panel-id])

  static css/CSS
  (local-rules [_] [[:.container {:flex           1
                                  :display        "flex"
                                  :flex-direction "column"}]])
  (include-children [_] [ui/ToolBar])

  Object
  (render [this]
    (let [{:keys []} (om/props this)
          css (css/get-classnames Panel)]
      (dom/div #js {:className (:container css)}
        (ui/toolbar {}
          (ui/toolbar-action {:onClick #(pick-element this)}
            (ui/icon :gps_fixed {})))
        "Element"))))

(def panel (om/factory Panel))
