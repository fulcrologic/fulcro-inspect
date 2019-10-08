(ns fulcro.inspect.ui.effects
  (:require [goog.dom :as gdom]
            [goog.object :as gobj]
            [goog.style :as gstyle]))

(defn make-ghost-out [node]
  (let [node' (.cloneNode node true)]
    (doto node'
      (gstyle/setStyle #js {:transition "all 500ms"
                            :position   "absolute"}))
    (gdom/insertSiblingBefore node' node)
    (js/requestAnimationFrame
      (fn []
        (.addEventListener node' "transitionend"
          (fn []
            (gdom/removeNode node')))
        (gstyle/setStyle node' #js {:opacity   "0"
                                    :transform "translate(0, -20px)"})))))

(defn animate-text-out [node text]
  (let [bounds (.getBoundingClientRect node)
        node   (js/document.createElement "div")]
    (doto node
      (gstyle/setStyle #js {:align-items     "center"
                            :background      "#fff"
                            :border-radius   "5px"
                            :box-shadow      "1px 1px 7px #00000038"
                            :color           "#000"
                            :display         "block"
                            :font-size       "12px"
                            :justify-content "center"
                            :left            (str (gobj/get bounds "left") "px")
                            :overflow        "hidden"
                            :padding         "3px 7px"
                            :position        "absolute"
                            :top             (str (gobj/get bounds "top") "px")
                            :transition      "all 600ms ease-out 0s"
                            :transform       "translate(-20px, -20px)"
                            :white-space     "nowrap"
                            :z-index         "10000"})
      (gdom/setTextContent text))
    (js/document.body.appendChild node)
    (js/requestAnimationFrame
      (fn []
        (.addEventListener node "transitionend"
          (fn []
            (gdom/removeNode node)))
        (gstyle/setStyle node #js {:opacity "0"
                                   ;:transform "translate(0, -20px)"
                                   })))))
