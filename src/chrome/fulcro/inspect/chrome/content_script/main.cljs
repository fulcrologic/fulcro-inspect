(ns fulcro.inspect.chrome.content-script.main
  (:require [goog.object :as gobj]
            [cljs.core.async :as async :refer [go go-loop chan <! >! put!]]))

(defonce active-messages* (atom {}))

(defn ack-message [msg]
  (go
    (let [id  (gobj/get msg "__fulcro-insect-msg-id")
          res (<! (get @active-messages* id))]
      (swap! active-messages* dissoc id)
      res)))

(defn setup-new-port []
  (let [port (js/chrome.runtime.connect)]
    (.addListener (gobj/get port "onMessage")
      (fn [msg]
        (when-let [ch (some->> (gobj/getValueByKeys msg "__fulcro-insect-msg-id")
                               (get @active-messages*))]
          (put! ch msg))))
    port))

(defn event-loop []
  (let [content-script->background-chan (chan (async/sliding-buffer 1024))
        port*                           (atom (setup-new-port))]

    (.addEventListener js/window "message"
      (fn [event]
        (when (and (= (.-source event) js/window)
                   (gobj/getValueByKeys event #js ["data" "fulcro-inspect-remote-message"]))
          (let [data (gobj/get event "data")
                id   (str (random-uuid))]
            (gobj/set data "__fulcro-insect-msg-id" id)
            (swap! active-messages* assoc id (async/promise-chan))
            (put! content-script->background-chan data))))
      false)

    (go-loop []
      (when-let [data (<! content-script->background-chan)]
        ; keep trying to send
        (loop []
          (.postMessage @port* data)
          (let [timer (async/timeout 1000)
                acker (ack-message data)
                [_ c] (async/alts! [acker timer] :priority true)]
            (when (= c timer)
              (reset! port* (setup-new-port))
              (recur))))
        (recur)))

    (js/console.log "LOOP READY"))

  :ready)

(defonce start (event-loop))
