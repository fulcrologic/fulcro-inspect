(ns fulcro.inspect.chrome.background.main
  (:require [goog.object :as gobj]))

(defonce connections* (atom {}))

(js/chrome.runtime.onConnect.addListener
  (fn [port]
    (js/console.log "NEW CONNECTION" port)
    (letfn [(listener [message port' send-response]
              (js/console.log "BG GOT MESSAGE" message @connections* port')
              (cond
                (= "init" (.-name message))
                (swap! connections* assoc (.-tabId message) port)

                :else
                (let [tab-id (gobj/getValueByKeys port' #js ["sender" "tab" "id"])]
                  (if (contains? @connections* tab-id)
                    (.postMessage (get @connections* tab-id) message)
                    (js/console.log "Tab not found in connection list.")))))]
      (.. port -onMessage (addListener listener))
      (.. port -onDisconnect
        (addListener (fn [port]
                       (.. port -onMessage (removeListener listener))
                       (if-let [port-key (->> @connections*
                                              (keep (fn [[k v]] (if (= v port) k)))
                                              (first))]
                         (swap! connections* dissoc port-key))))))))

(js/chrome.runtime.onMessage.addListener
  (fn [request sender send-response]
    (js/console.log "RUNTIME MSG" request)
    (if (.-tab sender)
      (let [tab-id (.. sender -tab -id)]
        (if (contains? @connections* tab-id)
          (.postMessage (get @connections* tab-id) request)
          (js/console.log "Tab not found in connection list."))))

    true))

(js/console.log "background init")
