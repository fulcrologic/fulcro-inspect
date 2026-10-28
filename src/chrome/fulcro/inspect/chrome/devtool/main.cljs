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
    [fulcro.inspect.ui.settings :as settings]
    [fulcro.inspect.ui-parser :as ui-parser]))

(defn ?handle-local-message [responses* type data]
  (common/handle-settings-message! responses* type data))

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
                     {:client-did-mount (fn [app] (settings/load-settings app))
                      :props-middleware (comp/wrap-update-extra-props
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
