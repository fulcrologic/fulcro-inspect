(ns com.wsscode.oge.ui.common
  (:require
    [com.fulcrologic.fulcro-css.css :as css]
    [com.fulcrologic.fulcro.components :as fp]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.wsscode.oge.ui.helpers :as helpers]
    [fulcro.inspect.ui.core :as cui]))

(def css-button
  {:color            (cui/color :text-on-accent)
   :cursor           "pointer"
   :background-color (cui/color :oge-button)
   :border-color     (cui/color :oge-button)
   :display          "inline-block"
   :font-weight      "400"
   :line-height      "1.25"
   :text-align       "center"
   :white-space      "nowrap"
   :vertical-align   "middle"
   :user-select      "none"
   :border           "1px solid transparent"
   :padding          ".5rem 1rem"
   :font-size        "1rem"
   :border-radius    ".25rem"
   :transition       "all .2s ease-in-out"})

(def css-button-hover
  {:color            (cui/color :text-on-accent)
   :background-color (cui/color :oge-button-hover)
   :border-color     (cui/color :oge-button-hover-border)})

(fp/defsc Button [this props]
  {:css [[:.button css-button
          [:&:hover css-button-hover]]]}
  (let [css (css/get-classnames Button)]
    (dom/button (helpers/props->html {:className (:button css)} props)
      (fp/children this))))

(def button (fp/factory Button))

(def css-input
  {:display          "block"
   :width            "100%"
   :padding          ".5rem .75rem"
   :font-size        "1rem"
   :line-height      "1.25"
   :color            (cui/color :oge-input-text)
   :background-color (cui/color :bg)
   :background-image "none"
   :background-clip  "padding-box"
   :border           (str "1px solid " (cui/color :oge-input-border))
   :border-radius    ".25rem"
   :transition       "border-color ease-in-out .15s,box-shadow ease-in-out .15s"})

(def css-input-focus
  {:color            (cui/color :oge-input-text)
   :background-color (cui/color :bg)
   :border-color     (cui/color :oge-input-focus)
   :outline          "0"})

(fp/defsc TextField [this props]
  {:css [[:.input css-input
          [:&:focus css-input-focus]
          [:&.success {:border-color (cui/color :success)}]
          [:&.warning {:border-color (cui/color :warning)}]]]}
  (let [css (css/get-classnames TextField)]
    (dom/input (helpers/props->html {:className (:input css)
                                     :type      "text"}
                 (helpers/expand-classes css (::classes props))
                 props))))

(def text-field (fp/factory TextField))

(fp/defsc CSS [this props]
  {:css-include [Button TextField]})
