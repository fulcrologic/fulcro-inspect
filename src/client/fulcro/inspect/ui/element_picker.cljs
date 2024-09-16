(ns fulcro.inspect.ui.element-picker
  (:require [clojure.string :as str]
            [com.fulcrologic.fulcro-css.css :as css]
            [goog.object :as gobj]
            [goog.dom :as gdom]
            [goog.style :as gstyle]
            [com.fulcrologic.fulcro.dom]
            [com.fulcrologic.fulcro.components :as fp :refer [get-query]]
            [fulcro.inspect.ui.helpers :as ui.h]
            [fulcro.inspect.remote.transit :as encode]))

(fp/defsc MarkerCSS [_ _]
  {:css [[:.container {:position       "absolute"
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
                      :border-top   "8px solid #333740"}]]]})

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

(defn react-raw-instance [node]
  (if-let [instance-key (->> (gobj/getKeys node)
                             (filter #(str/starts-with? % "__reactInternalInstance$"))
                             (first))]
    (gobj/get node instance-key)))

(defn react-instance [node]
  (if-let [raw (react-raw-instance node)]
    (or (gobj/getValueByKeys raw "_currentElement" "_owner" "_instance") ; react < 16
        (gobj/getValueByKeys raw "return" "stateNode") ; react >= 16
        )))

(defn ensure-reconciler [x app-uuid]
  (try
    (when (some-> (fp/get-reconciler x) fp/app-state deref
            :fulcro.inspect.core/app-uuid (= app-uuid))
      x)
    (catch :default _)))

(defn pick-element [{::keys                    [on-pick]
                     :fulcro.inspect.core/keys [app-uuid]
                     :or                       {on-pick identity}}]
  (let [marker       (marker-element)
        marker-label (marker-label-element)
        current      (atom nil)
        over-handler (fn [e]
                       (let [target (.-target e)]
                         (loop [target target]
                           (if target
                             (if-let [instance (some-> target react-instance (ensure-reconciler app-uuid))]
                               (do
                                 (.stopPropagation e)
                                 (reset! current instance)
                                 (gdom/setTextContent marker-label (ui.h/react-display-name instance))

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
                                          :top    (str (.-y offset) "px")})))
                               (recur (gdom/getParentElement target)))))))
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
  {:fulcro.inspect.ui.element/display-name (some-> comp ui.h/react-display-name)
   :fulcro.inspect.ui.element/props        (fp/props comp)
   :fulcro.inspect.ui.element/ident        (try
                                             (fp/get-ident comp)
                                             (catch :default _ nil))
   :fulcro.inspect.ui.element/query        (try
                                             (some-> comp fp/react-type fp/get-query)
                                             (catch :default _ nil))})
