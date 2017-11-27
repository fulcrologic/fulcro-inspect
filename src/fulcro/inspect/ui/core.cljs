(ns fulcro.inspect.ui.core
  (:require [om.next :as om]
            [fulcro-css.css :as css]
            [fulcro.ui.icons :as icons]
            [fulcro.inspect.ui.helpers :as h]
            [garden.selectors :as gs]
            [om.dom :as dom]))

(def mono-font-family "monospace")

(def label-font-family "sans-serif")
(def label-font-size "12px")

(def color-bg-light "#f5f5f5")
(def color-bg-light-border "#e1e1e1")
(def color-bg-medium-border "#cdcdcd")

(def color-text-normal "#5a5a5a")
(def color-text-strong "#333")
(def color-text-faded "#bbb")

(def color-icon-normal "#6e6e6e")
(def color-icon-strong "#333")

(def color-row-hover "#eef3fa")
(def color-row-selected "#e6e6e6")

(def css-info-group
  {:border-top "1px solid #eee"
   :padding    "7px 0"})

(def css-info-label
  {:color         color-text-normal
   :margin-bottom "6px"
   :font-weight   "bold"
   :font-family   label-font-family
   :font-size     "13px"})

(def css-timestamp
  {:font-family "monospace"
   :font-size   "11px"
   :color       "#808080"
   :margin      "0 4px 0 7px"})

;;; helpers

(defn add-zeros [n x]
  (loop [n (str n)]
    (if (< (count n) x)
      (recur (str 0 n))
      n)))

(defn print-timestamp [date]
  (str (add-zeros (.getHours date) 2) ":"
       (add-zeros (.getMinutes date) 2) ":"
       (add-zeros (.getSeconds date) 2) ":"
       (add-zeros (.getMilliseconds date) 3)))

;;; elements

(defn icon
  ([name] (icons/icon name))
  ([name props]
   (let [defaults {}]
     (apply icons/icon name (apply concat (merge defaults props))))))

(om/defui ^:once ToolBar
  static css/CSS
  (local-rules [_] [[:.container {:border-bottom "1px solid #dadada"
                                  :display       "flex"
                                  :align-items   "center"}
                     [:$c-icon {:fill      color-icon-normal
                                :transform "scale(0.7)"}
                      [:&:hover {:fill color-icon-strong}]]

                     [:&.details {:background    "#f3f3f3"
                                  :border-bottom "1px solid #ccc"
                                  :display       "flex"
                                  :align-items   "center"
                                  :height        "28px"}]]

                    [:.action {:cursor      "pointer"
                               :display     "flex"
                               :align-items "center"}]

                    [:.separator {:background "#ccc"
                                  :width      "1px"
                                  :height     "16px"
                                  :margin     "0 6px"}]

                    [:.input {:color       color-text-normal
                              :outline     "0"
                              :margin      "0 2px"
                              :font-family label-font-family
                              :font-size   label-font-size
                              :padding     "2px 4px"}]])
  (include-children [_] [])

  Object
  (render [this]
    (let [{:keys []} (om/props this)
          css (css/get-classnames ToolBar)]
      (dom/div (h/props+classes this {:className (:container css)})
        (om/children this)))))

(def toolbar (h/container-factory ToolBar))

(defn toolbar-separator []
  (dom/div #js {:className (:separator (css/get-classnames ToolBar))}))

(defn toolbar-spacer []
  (dom/div #js {:style #js {:flex 1}}))

(defn toolbar-action [props & children]
  (apply dom/div (h/props->html {:className (:action (css/get-classnames ToolBar))} props)
    children))

(defn toolbar-text-field [props]
  (dom/input (h/props->html {:className (:input (css/get-classnames ToolBar))
                             :type      "text"} props)))

(om/defui ^:once CSS
  static css/CSS
  (local-rules [_] [[:.focused-panel {:border-top     "1px solid #a3a3a3"
                                      :display        "flex"
                                      :flex-direction "column"
                                      :height         "50%"}]
                    [:.focused-container {:flex     "1"
                                          :overflow "auto"
                                          :padding  "0 10px"}]

                    [:.info-group css-info-group
                     [(gs/& gs/first-child) {:border-top "0"}]]
                    [:.info-label css-info-label]])
  (include-children [_] [ToolBar]))

(def scss (css/get-classnames CSS))

(defn focus-panel [props & children]
  (apply dom/div (h/props->html {:className (:focused-panel scss)}
                                props)
    children))

(defn focus-panel-content [props & children]
  (apply dom/div (h/props->html {:className (:focused-container scss)}
                                props)
    children))

(defn info [{::keys [title] :as props} & children]
  (apply dom/div (h/props->html {:className (:info-group scss)} props)
    (if title
      (dom/div #js {:className (:info-label scss)} title))
    children))
