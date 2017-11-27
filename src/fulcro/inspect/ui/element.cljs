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

(om/defui ^:once Details
  static fulcro/InitialAppState
  (initial-state [_ {::keys [props query] :as params}]
    (merge
      {::detail-id  (random-uuid)
       ::props-view (fulcro/get-initial-state data-viewer/DataViewer props)
       ::query-view (fulcro/get-initial-state data-viewer/DataViewer query)}
      params))

  static om/Ident
  (ident [_ props] [::detail-id (::detail-id props)])

  static om/IQuery
  (query [_] [::detail-id ::display-name ::ident
              {::props-view (om/get-query data-viewer/DataViewer)}
              {::query-view (om/get-query data-viewer/DataViewer)}])

  static css/CSS
  (local-rules [_] [[:.container {:flex     "1"
                                  :overflow "auto"
                                  :padding  "0 10px"}]])
  (include-children [_] [])

  Object
  (render [this]
    (let [{::keys [display-name ident props-view query-view]} (om/props this)
          css (css/get-classnames Details)]
      (dom/div #js {:className (:container css)}
        (ui/info {::ui/title "Display Name"}
          (ui/comp-display-name {} display-name))
        (ui/info {::ui/title "Ident"}
          (ui/ident {} ident))
        (ui/info {::ui/title "Props"}
          (data-viewer/data-viewer props-view))
        (ui/info {::ui/title "Query"}
          (data-viewer/data-viewer query-view))))))

(def details (om/factory Details))

;; picker

(om/defui ^:once MarkerCSS
  static css/CSS
  (local-rules [_] [[:.container {:position       "absolute"
                                  :display        "none"
                                  :background     "rgba(67, 132, 208, 0.5)"
                                  :pointer-events "none"
                                  :overflow       "hidden"
                                  :color          "#fff"
                                  :padding        "3px 5px"
                                  :box-sizing     "border-box"
                                  :font-family    "monospace"
                                  :font-size      "12px"
                                  :z-index        "999999"}]

                    [:.label {:position       "absolute"
                              :display        "none"
                              :pointer-events "none"
                              :box-sizing     "border-box"
                              :font-family    "sans-serif"
                              :font-size      "10px"
                              :z-index        "999999"

                              :background     "#333740"
                              :border-radius  "3px"
                              :padding        "6px 8px"
                              ;:color          "#ee78e6"
                              :color          "#ffab66"
                              :font-weight    "bold"
                              :white-space    "nowrap"}
                     [:&:before {:content      "\"\""
                                 :position     "absolute"
                                 :top          "24px"
                                 :left         "9px"
                                 :width        "0"
                                 :height       "0"
                                 :border-left  "8px solid transparent"
                                 :border-right "8px solid transparent"
                                 :border-top   "8px solid #333740"}]]])
  (include-children [_]))

(defn marker-element []
  (let [id "__fulcro_inspect_marker"]
    (or (js/document.getElementById id)
        (doto (js/document.createElement "div")
          (gobj/set "id" id)
          (gobj/set "className" (-> MarkerCSS css/get-classnames :container))
          (->> (gdom/appendChild js/document.body))))))

(defn marker-label-element []
  (let [id "__fulcro_inspect_marker_label"]
    (or (js/document.getElementById id)
        (doto (js/document.createElement "div")
          (gobj/set "id" id)
          (gobj/set "className" (-> MarkerCSS css/get-classnames :label))
          (->> (gdom/appendChild js/document.body))))))

(defn react-instance [node]
  (if-let [instance-key (->> (gobj/getKeys node)
                             (filter #(str/starts-with? % "__reactInternalInstance$"))
                             (first))]
    (gobj/get node instance-key)))

(defn ensure-reconciler [x app]
  (try
    (when (some-> (om/get-reconciler x) om/app-state deref
                  :fulcro.inspect.core/app-id (= app))
      x)
    (catch :default _)))

(defn pick-element [{::keys [on-pick app-id]
                     :or    {on-pick identity}}]
  (let [marker       (marker-element)
        marker-label (marker-label-element)
        current      (atom nil)
        over-handler (fn [e]
                       (let [target (.-target e)]
                         (when-let [instance (some-> target
                                                     (react-instance)
                                                     (gobj/getValueByKeys #js ["_currentElement" "_owner" "_instance"])
                                                     (ensure-reconciler app-id))]
                           (.stopPropagation e)
                           (reset! current instance)
                           (gdom/setTextContent marker-label (-> instance om/react-type (gobj/get "displayName")))

                           (let [target' (js/ReactDOM.findDOMNode instance)
                                 offset  (gstyle/getPageOffset target')
                                 size    (gstyle/getSize target')]
                             (gstyle/setStyle marker-label
                               #js {:left (str (.-x offset) "px")
                                    :top  (str (- (.-y offset) 36) "px")})

                             (gstyle/setStyle marker
                               #js {:width  (str (.-width size) "px")
                                    :height (str (.-height size) "px")
                                    :left   (str (.-x offset) "px")
                                    :top    (str (.-y offset) "px")})))))
        pick-handler (fn self []
                       (on-pick @current)

                       (gstyle/setStyle marker #js {:display "none"})
                       (gstyle/setStyle marker-label #js {:display "none"})

                       (js/removeEventListener "click" self)
                       (js/removeEventListener "mouseover" over-handler))]

    (gstyle/setStyle marker #js {:display "block"
                                 :top     "-100000px"
                                 :left    "-100000px"})

    (gstyle/setStyle marker-label #js {:display "block"
                                       :top     "-100000px"
                                       :left    "-100000px"})

    (js/addEventListener "mouseover" over-handler)

    (js/setTimeout
      #(js/addEventListener "click" pick-handler)
      10)))

(defn inspect-component [comp]
  {::display-name (some-> comp om/react-type (gobj/get "displayName"))
   ::props        (om/props comp)
   ::ident        (try
                    (om/get-ident comp)
                    (catch :default _ nil))
   ::query        (try
                    (some-> comp om/react-type om/get-query)
                    (catch :default _ nil))})

(mutations/defmutation set-element [details]
  (action [env]
    (h/swap-entity! env assoc :ui/picking? false)
    (h/swap-in! env [::details] assoc ::ident nil)
    (h/remove-edge! env ::details)
    (h/create-entity! env Details details :set ::details)))

(om/defui ^:once Panel
  static fulcro/InitialAppState
  (initial-state [this _]
    {::panel-id (random-uuid)})

  static om/Ident
  (ident [_ props] [::panel-id (::panel-id props)])

  static om/IQuery
  (query [_] [::panel-id :ui/picking?
              {::details (om/get-query Details)}])

  static css/CSS
  (local-rules [_] [[:.container {:flex           1
                                  :display        "flex"
                                  :flex-direction "column"}
                     [:.icon-active
                      [:svg.c-icon {:fill "#4682E9"}]]]])
  (include-children [_] [ui/CSS MarkerCSS Details])

  Object
  (render [this]
    (let [{:keys [ui/picking? ::panel-id] :as props} (om/props this)
          css (css/get-classnames Panel)]
      (dom/div #js {:className (:container css)}
        (ui/toolbar {}
          (ui/toolbar-action (cond-> {:onClick #(do
                                                  (mutations/set-value! this :ui/picking? true)
                                                  (pick-element {::app-id  (second panel-id)
                                                                 ::on-pick (fn [comp]
                                                                             (let [details (inspect-component comp)]
                                                                               (om/transact! this [`(set-element ~details)])))}))}
                               picking? (assoc :className (:icon-active css)))
            (ui/icon :gps_fixed {})))
        (if (::details props)
          (details (::details props)))))))

(def panel (om/factory Panel))
