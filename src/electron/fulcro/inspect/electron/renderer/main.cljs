(ns fulcro.inspect.electron.renderer.main
  (:require
    [cljs.core.async :refer [<! go put!]]
    [com.fulcrologic.devtools.electron.devtool :as edt]
    [com.fulcrologic.fulcro-css.css :as css]
    [com.fulcrologic.fulcro.algorithms.tx-processing :as txn]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as fp]
    [com.fulcrologic.statecharts.integration.fulcro :as scf]
    [edn-query-language.core :as eql]
    [fulcro.inspect.common :as common :refer [global-inspector*]]
    [fulcro.inspect.lib.history :as hist]
    [fulcro.inspect.ui-parser :as ui-parser]
    [fulcro.inspect.ui.multi-inspector :as multi-inspector]
    [taoensso.timbre :as log]))

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

(defn make-network [parser responses*]
  (let [parser-env {:responses* responses*}]
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
        app        (app/fulcro-app {; :client-did-mount (fn [app] (settings/load-settings app))
                                    :props-middleware (fp/wrap-update-extra-props
                                                        (fn [cls extra-props]
                                                          (merge extra-props (css/get-classnames cls))))


                                    :shared
                                    {::hist/db-hash-index               (atom {})
                                     :fulcro.inspect.renderer/electron? true}

                                    :remotes          {:remote
                                                       (make-network (ui-parser/parser) responses*)}})]
    (reset! global-inspector* app)
    (edt/add-devtool-remote! app)
    (scf/install-fulcro-statecharts! app)
    (app/mount! app common/GlobalRoot "app")))

(defn global-inspector
  ([] @global-inspector*)
  ([options]
   (start-global-inspector options)
   @global-inspector*))

(defn start []
  (if @global-inspector*
    (app/mount! @global-inspector* common/GlobalRoot "app")
    (start-global-inspector {})))

(start)
