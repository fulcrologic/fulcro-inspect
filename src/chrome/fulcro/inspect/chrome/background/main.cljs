(ns fulcro.inspect.chrome.background.main)

(defonce connections* (atom {}))

(js/chrome.runtime.onConnect.addListener
  (fn [port]
    (letfn [(listener [message sender send-response]
              (cond
                (= "init" (.-name message))
                (swap! connections* assoc (.-tabId message) port)))]
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
    (if (.-tab sender)
      (let [tab-id (.. sender -tab -id)]
        (if (contains? @connections* tab-id)
          (.postMessage (get @connections* tab-id) request)
          (js/console.log "Tab not found in connection list."))))

    true))

(js/console.log "background init")
