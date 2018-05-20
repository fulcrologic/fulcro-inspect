(ns fulcro.inspect.chrome.background.main
  (:require [goog.object :as gobj]
            [cljs.core.async :as async :refer [go go-loop chan <! >! put!]]))

(defonce tools-conns* (atom {}))
(defonce tools-waiters* (atom {}))

(defn handle-message [devtool-port ch message port]
  (js/console.log "BG GOT MESSAGE" message @tools-conns* port)
  (cond
    (= "init" (.-name message))
    (let [tab-id (.-tabId message)]
      (swap! tools-conns* assoc tab-id devtool-port)
      (some-> (get @tools-waiters* tab-id) (put! :ready)))

    (gobj/getValueByKeys message "fulcro-inspect-remote-message")
    (let [tab-id (gobj/getValueByKeys port "sender" "tab" "id")]
      (put! ch {:tab-id tab-id :message message})

      ; ack message received
      (when-let [id (gobj/getValueByKeys message "__fulcro-insect-msg-id")]
        (.postMessage port #js {:ack "ok" "__fulcro-insect-msg-id" id})))))

(js/chrome.runtime.onConnect.addListener
  (fn [port]
    (js/console.log "NEW CONNECTION" port)
    (let [background->devtool-chan (chan (async/sliding-buffer 1024))
          listener                 (partial handle-message port background->devtool-chan)]
      (.. port -onMessage (addListener listener))
      (.. port -onDisconnect
        (addListener (fn [port]
                       (.. port -onMessage (removeListener listener))
                       (if-let [port-key (->> @tools-conns*
                                              (keep (fn [[k v]] (if (= v port) k)))
                                              (first))]
                         (swap! tools-conns* dissoc port-key)))))

      (go-loop [replay nil]
        (when-let [{:keys [tab-id message] :as data} (or replay (<! background->devtool-chan))]
          ; send message to devtool
          (if (contains? @tools-conns* tab-id)
            (do
              (.postMessage (get @tools-conns* tab-id) message)
              (recur nil))
            (let [ch (async/promise-chan)]
              (swap! tools-waiters* assoc tab-id ch)
              (<! ch)
              (recur data))))))))
