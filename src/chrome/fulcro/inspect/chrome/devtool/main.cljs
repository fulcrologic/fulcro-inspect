(ns fulcro.inspect.chrome.devtool.main
  (:require
    [cljs.core.async :as async :refer [<! go put!]]
    [com.fulcrologic.devtools.chrome.devtool :as dt]
    [com.fulcrologic.fulcro-css.css :as css]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.fulcro.networking.mock-server-remote :as mock-net]
    [com.fulcrologic.statecharts.integration.fulcro :as scf]
    [fulcro.inspect.common :as common :refer [GlobalRoot global-inspector* websockets?]]
    [fulcro.inspect.lib.history :as hist]
    [fulcro.inspect.lib.local-storage :as storage]
    [fulcro.inspect.ui-parser :as ui-parser]
    [taoensso.timbre :as log]))

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

(defn make-network [port* parser responses*]
  (mock-net/mock-http-server
    {:parser (fn [edn]
               (go
                 (async/<!
                   (parser
                     {:send-message (fn [type data]
                                      (?handle-local-message responses* type data))
                      :responses*   responses*}
                     edn))))}))

(defn start-global-inspector []
  (let [port*      (atom nil)
        responses* (atom {})
        app        (app/fulcro-app
                     {:props-middleware (comp/wrap-update-extra-props
                                          (fn [cls extra-props]
                                            (merge extra-props (css/get-classnames cls))))

                      :shared
                      {::hist/db-hash-index (atom {})}

                      :remotes          {:remote
                                         (make-network port* (ui-parser/parser) responses*)}})]
    (dt/add-devtool-remote! app)
    (scf/install-fulcro-statecharts! app)
    (app/mount! app GlobalRoot "app")
    app))

(defn global-inspector
  []
  (or @global-inspector*
    (reset! global-inspector* (start-global-inspector))))

(global-inspector)
