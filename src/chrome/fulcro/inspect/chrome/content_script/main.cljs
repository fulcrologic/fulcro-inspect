(ns fulcro.inspect.chrome.content-script.main
  (:require [goog.object :as gobj]
            [cljs.core.async :refer [go go-loop chan <! >! put!]]))

(defn event-loop []
  (let [port (js/chrome.runtime.connect)]
    (.addEventListener js/window "message"
      (fn [event]
        (js/console.log "PAGE EVT" event port)
        (if (= (.-source event) js/window)
          (.postMessage port (.-data event))))
      false)))

(defonce start (event-loop))
