(ns fulcro.inspect.electron.renderer.main
  (:require
    [cljs.core.async :refer [<! go put!]]
    [com.fulcrologic.fulcro-css.css :as css]
    [com.fulcrologic.fulcro.algorithms.tx-processing :as txn]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.application]
    [com.fulcrologic.fulcro.components :as fp]
    [com.fulcrologic.statecharts.integration.fulcro :as scf]
    [edn-query-language.core :as eql]
    [fulcro.inspect.common :as common :refer [app-uuid-key global-inspector* last-disposed-app*]]
    [fulcro.inspect.lib.history :as hist]
    [fulcro.inspect.remote.transit :as encode]
    [fulcro.inspect.ui-parser :as ui-parser]
    [fulcro.inspect.ui.multi-inspector :as multi-inspector]
    [fulcro.inspect.ui.settings :as settings]
    [taoensso.timbre :as log]))

(vreset! common/websockets? true)

(def ipcRenderer js/window.ipcRenderer)

(defonce ^:private dom-node (atom nil))

(def current-tab-id 42)

;; LANDMARK: This is how we talk back to the node server, which can send the websocket message
(defn post-message [type data]
  (.send ipcRenderer "event"
    #js {:fulcro-inspect-devtool-message (encode/write {:type type :data data :timestamp (js/Date.)})
         :client-connection-id           (encode/write (:fulcro.inspect.core/client-connection-id data))
         :app-uuid                       (encode/write (:fulcro.inspect.core/app-uuid data))
         :tab-id                         current-tab-id}))

(defn handle-local-message [{:keys [responses*]} event]
  (when-let [{:keys [type data]} (common/event-data event)]
    (case type
      :fulcro.inspect.client/message-response
      (when-let [res-chan (get @responses* (::ui-parser/msg-id data))]
        (put! res-chan (::ui-parser/msg-response data)))

      :fulcro.inspect.client/toggle-settings
      (fp/transact! @global-inspector*
        [(multi-inspector/toggle-settings data)]
        {:ref [::multi-inspector/multi-inspector "main"]})

      nil)))

(defn event-loop! [_app responses*]
  (.on ipcRenderer "event"
    (fn [event]
      (or
        (common/handle-remote-message {:responses* responses*
                                       :event      event})
        (handle-local-message {:responses* responses*} event)))))

(defn make-network [parser responses*]
  (let [parser-env {:send-message post-message
                    :responses*   responses*}]
    {:transmit! (fn transmit! [_ {:keys [::txn/ast ::txn/result-handler ::txn/update-handler]}]
                  (go
                    (try
                      (let [edn  (eql/ast->query ast)
                            body (<! (parser parser-env edn))]
                        (result-handler {:status-code 200 :body body}))
                      (catch :default e
                        (log/error e "Handler failed")
                        (result-handler {:status-code 500 :body {}})))))}))

(defn start-global-inspector [_options]
  (let [responses* (atom {})
        app        (app/fulcro-app {:client-did-mount
                                    (fn [app]
                                      (event-loop! app responses*)
                                      (settings/load-settings app))

                                    :props-middleware (fp/wrap-update-extra-props
                                                        (fn [cls extra-props]
                                                          (merge extra-props (css/get-classnames cls))))


                                    :shared
                                    {::hist/db-hash-index               (atom {})
                                     :fulcro.inspect.renderer/electron? true}

                                    :remotes          {:remote
                                                       (make-network (ui-parser/parser) responses*)}})
        node       (js/document.createElement "div")]
    (js/document.body.appendChild node)
    (reset! global-inspector* app)
    (app/mount! app common/GlobalRoot node)
    (scf/install-fulcro-statecharts! app)
    (reset! dom-node node)))

(defn global-inspector
  ([] @global-inspector*)
  ([options]
   (start-global-inspector options)
   @global-inspector*))

(defn start []
  (if @global-inspector*
    (app/mount! @global-inspector* common/GlobalRoot @dom-node)
    (start-global-inspector {})))

(start)
