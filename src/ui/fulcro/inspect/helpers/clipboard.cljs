(ns fulcro.inspect.helpers.clipboard
  (:require [goog.object :as gobj]))

(defn copy-to-clipboard [string]
  (let [string   string
        el       (doto (js/document.createElement "textarea")
                   (gobj/set "value" string)
                   (.setAttribute "readonly" "")
                   (gobj/set "style" #js {:position "absolute" :left "-9999px"}))
        selected (if (> (gobj/get (js/document.getSelection) "rangeCount") 0)
                   (.getRangeAt (js/document.getSelection) 0))]
    (js/document.body.appendChild el)
    (.select el)
    (js/document.execCommand "copy")
    (js/document.body.removeChild el)

    (when selected
      (.removeAllRanges (js/document.getSelection))
      (.addRange (js/document.getSelection) selected))))
