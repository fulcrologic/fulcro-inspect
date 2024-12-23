(ns com.wsscode.oge.ui.common
  (:require
    [com.fulcrologic.fulcro-css.css :as css]
    [com.fulcrologic.fulcro.components :as fp]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.wsscode.oge.ui.helpers :as helpers]))

(def css-button
  {:color            "#fff"
   :cursor           "pointer"
   :background-color "#0275d8"
   :border-color     "#0275d8"
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
  {:color            "#fff"
   :background-color "#025aa5"
   :border-color     "#01549b"})

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
   :color            "#464a4c"
   :background-color "#fff"
   :background-image "none"
   :background-clip  "padding-box"
   :border           "1px solid rgba(0,0,0,.15)"
   :border-radius    ".25rem"
   :transition       "border-color ease-in-out .15s,box-shadow ease-in-out .15s"})

(def css-input-focus
  {:color            "#464a4c"
   :background-color "#fff"
   :border-color     "#5cb3fd"
   :outline          "0"})

(fp/defsc TextField [this props]
  {:css [[:.input css-input
          [:&:focus css-input-focus]
          [:&.success {:border-color "#5cb85c"}]
          [:&.warning {:border-color "#f0ad4e"}]]]}
  (let [css (css/get-classnames TextField)]
    (dom/input (helpers/props->html {:className (:input css)
                                     :type      "text"}
                 (helpers/expand-classes css (::classes props))
                 props))))

(def text-field (fp/factory TextField))

(fp/defsc CSS [this props]
  {:css-include [Button TextField]})
