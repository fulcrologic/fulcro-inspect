(ns fulcro.inspect.chrome.background.main
  (:require [goog.object :as gobj]
            [cljs.core.async :as async :refer [go go-loop chan <! >! put!]]
            [taoensso.timbre :as log]))

(defonce remote-conns* (atom {}))
(defonce tools-conns* (atom {}))

(defn handle-devtool-message [devtool-port message port]
  (cond
    (= "init" (gobj/get message "name"))
    (let [tab-id (gobj/get message "tab-id")]
      (swap! tools-conns* assoc tab-id devtool-port))

    (gobj/getValueByKeys message "fulcro-inspect-devtool-message")
    (let [tab-id      (gobj/get message "tab-id")
          remote-port (get @remote-conns* tab-id)]
      (when-not remote-port
        (println "WARN: No stored remote port for this sender tab"
          tab-id
          "Known tabs:" (keys @remote-conns*)))             ; FIXME rm
      (some-> remote-port (.postMessage message)))))

(defn set-icon-and-popup [tab-id]
  #_(js/chrome.action.setIcon                               ; FIXME Fails to be fetched
      #js {;:tabId tab-id
           :path #js {"16"  "icon-16.png"
                      "32"  "icon-32.png"
                      "48"  "icon-48.png"
                      "128" "icon-128.png"}})
  (js/chrome.action.setPopup
    #js {:tabId tab-id
         :popup "popups/enabled.html"}))

(defn handle-remote-message [ch message port]
  (log/info "Backgroud worker received message" message)
  (cond
    ; send message to devtool
    (gobj/getValueByKeys message "fulcro-inspect-remote-message")
    (let [tab-id (gobj/getValueByKeys port "sender" "tab" "id")]
      (put! ch {:tab-id tab-id :message message})

      ; ack message received
      (when-let [id (gobj/getValueByKeys message "__fulcro-insect-msg-id")]
        (.postMessage port #js {:ack "ok" "__fulcro-insect-msg-id" id})))

    ; set icon and popup
    (gobj/getValueByKeys message "fulcro-inspect-fulcro-detected")
    (let [tab-id (gobj/getValueByKeys port "sender" "tab" "id")]
      (set-icon-and-popup tab-id))))

(defn add-listener []
  (js/chrome.runtime.onConnect.addListener
    (fn [port]
      (case (log/spy :info (gobj/get port "name"))
        "fulcro-inspect-remote"
        (let [background->devtool-chan (chan (async/sliding-buffer 50000))
              listener                 (partial handle-remote-message background->devtool-chan)
              tab-id                   (gobj/getValueByKeys port "sender" "tab" "id")]

          (swap! remote-conns* assoc tab-id port)

          (println "DEBUG onConn listener msg="
            (gobj/get port "name")
            "for tab-id" tab-id "storing the port...")      ; FIXME rm


          (.addListener (gobj/get port "onMessage") listener)
          (.addListener (gobj/get port "onDisconnect")
            (fn [port]
              (.removeListener (gobj/get port "onMessage") listener)
              (swap! remote-conns* dissoc tab-id)
              (async/close! background->devtool-chan)))

          (go-loop []
            (when-let [{:keys [tab-id message] :as data} (<! background->devtool-chan)]
              ; send message to devtool
              (if (contains? @tools-conns* tab-id)
                (do
                  (.postMessage (get @tools-conns* tab-id) message)
                  (recur))
                (recur)))))

        "fulcro-inspect-devtool"
        (let [listener (partial handle-devtool-message port)]
          (.addListener (gobj/get port "onMessage") listener)
          (.addListener (gobj/get port "onDisconnect")
            (fn [port]
              (.removeListener (gobj/get port "onMessage") listener)
              (if-let [port-key (->> @tools-conns*
                                  (keep (fn [[k v]] (if (= v port) k)))
                                  (first))]
                (swap! tools-conns* dissoc port-key)))))

        (js/console.log "Ignoring connection" (gobj/get port "name"))))))

(defn init []
  (add-listener)
  (js/console.log "Fulcro service worker init done"))
