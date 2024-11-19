(ns fulcro.inspect.chrome.devtool.main
  (:require
    [cljs.core.async :as async :refer [<! go put!]]
    [com.fulcrologic.fulcro-css.css :as css]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.application]
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.fulcro.components]
    [com.fulcrologic.fulcro.networking.mock-server-remote :as mock-net]
    [com.fulcrologic.statecharts.integration.fulcro :as scf]
    [com.wsscode.common.async-cljs :refer [<?maybe]]
    [fulcro.inspect.common :as common :refer [GlobalRoot app-uuid-key global-inspector* websockets?]]
    [fulcro.inspect.lib.history :as hist]
    [fulcro.inspect.lib.local-storage :as storage]
    [fulcro.inspect.remote.transit :as encode]
    [fulcro.inspect.ui-parser :as ui-parser]
    [fulcro.inspect.ui.settings :as settings]
    [shadow.cljs.modern :refer [js-await]]
    [taoensso.timbre :as log]))

(def current-tab-id js/chrome.devtools.inspectedWindow.tabId)

;; LANDMARK: This is how we talk back to the content script
(defn post-message [port type data]
  (.postMessage port #js {:fulcro-inspect-devtool-message (encode/write {:type type :data data :timestamp (js/Date.)})
                          :tab-id                         current-tab-id}))

(defonce message-handler-ch (async/chan (async/dropping-buffer 1024)))

(defn ?handle-local-message [responses* type data]
  (case type
    :fulcro.inspect.client/load-settings
    (let [settings (into {}
                     (remove (comp #{::not-found} second))
                     (for [k (:query data)]
                       [k (storage/get k ::not-found)]))]
      (common/respond-to-load! responses* type
        {::ui-parser/msg-response settings
         ::ui-parser/msg-id       (::ui-parser/msg-id data)})
      :ok)
    :fulcro.inspect.client/save-settings
    (do
      (doseq [[k v] data]
        (log/trace "Saving setting:" k "=>" v)
        (storage/set! k v))
      :ok)
    #_else nil))

(defn event-loop [app responses*]
  (let [port (js/chrome.runtime.connect #js {:name "fulcro-inspect-devtool"})]
    (.addListener (.-onMessage port)
      (fn [msg]
        (put! message-handler-ch
          {:port       port
           :event      msg
           :responses* responses*})
        (js/Promise.resolve)))
    (go
      (loop []
        (when-let [msg (<! message-handler-ch)]
          (<?maybe (common/handle-remote-message msg))
          (recur))))

    (.postMessage port #js {:name "init" :tab-id current-tab-id})
    (post-message port :fulcro.inspect.client/request-page-apps {})

    port))

(defn make-network [port* parser responses*]
  (mock-net/mock-http-server
    {:parser (fn [edn]
               (go
                 (async/<!
                   (parser
                     {:send-message (fn [type data]
                                      (or
                                        (?handle-local-message responses* type data)
                                        (post-message @port* type data)))
                      :responses*   responses*}
                     edn))))}))

(defn start-global-inspector [options]
  (let [port*      (atom nil)
        responses* (atom {})
        app        (app/fulcro-app
                     {:props-middleware (comp/wrap-update-extra-props
                                          (fn [cls extra-props]
                                            (merge extra-props (css/get-classnames cls))))

                      :shared
                      {::hist/db-hash-index (atom {})}

                      :remotes          {:remote
                                         (make-network port* (ui-parser/parser) responses*)}})
        node       (js/document.createElement "div")]
    (js/document.body.appendChild node)
    ;; Sending a message of any kind will wake up the service worker, otherwise our comms won't succeed because
    ;; it will register for our initial comms AFTER we've sent them.
    ;; TASK: We must handle the service worker going away gracefully...not sure how to do that yet.
    (js-await [_ (js/chrome.runtime.sendMessage #js {:ping true})]
      (reset! port* (event-loop app responses*))
      (post-message @port* :fulcro.inspect.client/check-client-version {})
      (settings/load-settings app))
    (app/mount! app GlobalRoot node)
    (scf/install-fulcro-statecharts! app)
    app))

(defn global-inspector
  ([] @global-inspector*)
  ([options]
   (or @global-inspector*
     (reset! global-inspector* (start-global-inspector options)))))

(global-inspector {})
