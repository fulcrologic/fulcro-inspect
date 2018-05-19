(ns fulcro.inspect.chrome.background.main
  (:require [goog.object :as gobj]))

(defonce connections* (atom {}))

(defn handle-message [port message port' send-response]
  (js/console.log "BG GOT MESSAGE" message @connections* port')
  (cond
    (= "init" (.-name message))
    (swap! connections* assoc (.-tabId message) port)

    :else
    (let [tab-id (gobj/getValueByKeys port' #js ["sender" "tab" "id"])]
      (when (and (contains? @connections* tab-id)
                 (gobj/getValueByKeys message "fulcro-inspect-remote-message"))


        (.postMessage (get @connections* tab-id) message))))

  (when-let [id (gobj/getValueByKeys message "__fulcro-insect-msg-id")]
    (js/console.log "PING BACK" id)
    (.postMessage port' #js {:ack "ok" "__fulcro-insect-msg-id" id})))

(js/chrome.runtime.onConnect.addListener
  (fn [port]
    (js/console.log "NEW CONNECTION" port)
    (let [listener (partial handle-message port)]
      (.. port -onMessage (addListener listener))
      (.. port -onDisconnect
        (addListener (fn [port]
                       (.. port -onMessage (removeListener listener))
                       (if-let [port-key (->> @connections*
                                              (keep (fn [[k v]] (if (= v port) k)))
                                              (first))]
                         (swap! connections* dissoc port-key))))))))
