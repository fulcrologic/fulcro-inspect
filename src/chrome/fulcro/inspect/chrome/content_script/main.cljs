(ns fulcro.inspect.chrome.content-script.main
  (:require [goog.object :as gobj]
            [cljs.core.async :as async :refer [go go-loop chan <! >! put!]]
            [fulcro.inspect.remote.transit :as encode]))

(defonce active-messages* (atom {}))

(defn ack-message [msg]
  (go
    (let [id  (gobj/get msg "__fulcro-insect-msg-id")]
      (if-let [res (some-> (get @active-messages* id) (<!))]
        (do
          (swap! active-messages* dissoc id)
          res)
        nil))))

(defn envelope-ack [data]
  (let [id   (str (random-uuid))]
    (gobj/set data "__fulcro-insect-msg-id" id)
    (swap! active-messages* assoc id (async/promise-chan))
    data))

(defn setup-new-port []
  (let [port (js/chrome.runtime.connect #js {:name "fulcro-inspect-remote"})]
    (.addListener (gobj/get port "onMessage")
      (fn [msg]
        (cond
          (gobj/getValueByKeys msg "fulcro-inspect-devtool-message")
          (.postMessage js/window msg "*")

          :else
          (when-let [ch (some->> (gobj/getValueByKeys msg "__fulcro-insect-msg-id")
                                 (get @active-messages*))]
            (put! ch msg)))))
    port))

(defn event-loop []
  (when (js/document.documentElement.getAttribute "__fulcro-inspect-remote-installed__")
    (let [content-script->background-chan (chan (async/sliding-buffer 1024))
          port*                           (atom (setup-new-port))]

      ; set browser icon
      (.postMessage @port* #js {:fulcro-inspect-fulcro-detected true})

      ; clear inspector
      (put! content-script->background-chan
        (envelope-ack
          #js {:fulcro-inspect-remote-message
               (encode/write
                 {:type :fulcro.inspect.client/reset
                  :data {}})}))

      (.addEventListener js/window "message"
        (fn [event]
          (when (and (= (.-source event) js/window)
                     (gobj/getValueByKeys event "data" "fulcro-inspect-remote-message"))
            (put! content-script->background-chan (envelope-ack (gobj/get event "data")))))
        false)

      (go-loop []
        (when-let [data (<! content-script->background-chan)]
          ; keep trying to send
          (loop []
            (.postMessage @port* data)
            (let [timer (async/timeout 1000)
                  acker (ack-message data)
                  [_ c] (async/alts! [acker timer] :priority true)]
              ; restart the port in case of a timeout
              (when (= c timer)
                (reset! port* (setup-new-port))
                (recur))))
          (recur)))))

  :ready)

(defonce start (event-loop))
