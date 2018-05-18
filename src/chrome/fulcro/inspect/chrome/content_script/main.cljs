(ns fulcro.inspect.chrome.content-script.main
  (:require [goog.object :as gobj]
            [cljs.core.async :refer [go go-loop chan <! >! put!]]))

(defn event-loop []
  (let [port (js/chrome.runtime.connect)]
    (.addEventListener js/window "message"
      (fn [event]
        (if (= (.-source event) js/window)
          (.postMessage port (.-data event))))
      false)))

(event-loop)
