(ns fulcro.inspect.ui.element
  (:require
    [clojure.data :as data]
    [clojure.string :as str]
    [fulcro-css.css :as css]
    [fulcro.client.core :as fulcro :refer-macros [defsc]]
    [fulcro.client.mutations :as mutations]
    [fulcro.inspect.helpers :as h]
    [fulcro.inspect.ui.core :as ui]
    [fulcro.inspect.ui.data-viewer :as data-viewer]
    [goog.object :as gobj]
    [goog.dom :as gdom]
    [goog.style :as gstyle]
    [om.dom :as dom]
    [om.next :as om :refer [get-query]]))

(om/defui ^:once MarkerCSS
  static css/CSS
  (local-rules [_] [[:.container {:position       "absolute"
                                  :display        "none"
                                  :background     "rgba(0, 0, 0, 0.6)"
                                  :pointer-events "none"
                                  :overflow       "hidden"
                                  :color          "#fff"
                                  :padding        "3px 5px"
                                  :box-sizing     "border-box"
                                  :font-family    "monospace"
                                  :font-size      "12px"}]])
  (include-children [_]))

(defn marker-element []
  (let [id "__fulcro_inspect_marker"]
    (or (js/document.getElementById id)
        (let [node (doto (js/document.createElement "div")
                     (gobj/set "id" id)
                     (gobj/set "className" (-> MarkerCSS
                                               css/get-classnames
                                               :container)))]
          (gdom/appendChild js/document.body node)
          node))))

(defn react-instance [node]
  (if-let [instance-key (->> (gobj/getKeys node)
                             (filter #(str/starts-with? % "__reactInternalInstance$"))
                             (first))]
    (gobj/get node instance-key)))

(defn pick-element [{::keys [on-pick]
                     :or    {on-pick identity}}]
  (let [marker       (marker-element)
        current      (atom nil)
        over-handler (fn [e]
                       (let [target (.-target e)]
                         (when-let [instance (some-> target
                                                     (react-instance)
                                                     (gobj/getValueByKeys #js ["_currentElement" "_owner" "_instance"]))]
                           (.stopPropagation e)
                           (reset! current instance)
                           (gdom/setTextContent marker (-> instance om/react-type (gobj/get "displayName")))
                           (let [target' (js/ReactDOM.findDOMNode instance)
                                 offset  (gstyle/getPageOffset target')
                                 size    (gstyle/getSize target')]
                             (gstyle/setStyle marker
                               #js {:width  (str (.-width size) "px")
                                    :height (str (.-height size) "px")
                                    :left   (str (.-x offset) "px")
                                    :top    (str (.-y offset) "px")})))))
        pick-handler (fn self []
                       (on-pick @current)

                       (gstyle/setStyle marker #js {:display "none"
                                                    :top     "-100000px"
                                                    :left    "-100000px"})

                       (js/removeEventListener "click" self)
                       (js/removeEventListener "mouseover" over-handler))]

    (gstyle/setStyle marker #js {:display "block"})
    (js/addEventListener "mouseover" over-handler)

    (js/setTimeout
      #(js/addEventListener "click" pick-handler)
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
  (include-children [_] [ui/ToolBar MarkerCSS])

  Object
  (render [this]
    (let [{:keys []} (om/props this)
          css (css/get-classnames Panel)]
      (dom/div #js {:className (:container css)}
        (ui/toolbar {}
          (ui/toolbar-action {:onClick #(pick-element {::on-pick (fn [comp]
                                                                   (js/console.log "Picked" comp))})}
            (ui/icon :gps_fixed {})))
        "Element"))))

(def panel (om/factory Panel))
