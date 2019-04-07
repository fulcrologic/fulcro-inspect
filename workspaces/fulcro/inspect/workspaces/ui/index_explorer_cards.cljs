(ns fulcro.inspect.workspaces.ui.index-explorer-cards
  (:require [nubank.workspaces.lib.fulcro-portal :as f.portal]
            [nubank.workspaces.card-types.fulcro :as ct.fulcro]
            [com.wsscode.pathom.fulcro.network :as f.network]
            [nubank.workspaces.core :as ws]
            [nubank.workspaces.model :as wsm]
            [fulcro.inspect.ui-parser :as ui-parser]
            [fulcro.inspect.ui.index-explorer :as fi.iex]
            [com.wsscode.pathom.viz.index-explorer :as iex]
            [com.wsscode.pathom.core :as p]
            [com.wsscode.pathom.connect :as pc]
            [cljs.core.async :as async]))

(pc/defresolver iex-index [env _]
  {::pc/input  #{::iex/id}
   ::pc/output [::iex/index]}
  {::iex/index (get env ::pc/indexes)})

(def parser
  (p/parser
    {::p/env     {::p/reader               [p/map-reader
                                            pc/reader2
                                            pc/open-ident-reader
                                            p/env-placeholder-reader]
                  ::p/placeholder-prefixes #{">"}}
     ::p/mutate  pc/mutate
     ::p/plugins [(pc/connect-plugin {::pc/register [iex-index]})
                  p/error-handler-plugin
                  p/request-cache-plugin
                  p/trace-plugin]}))

(defn respond [responses msg response-data]
  (let [out-chan (get @responses (::ui-parser/msg-id msg))]
    (async/put! out-chan response-data)))

(defn send-message [responses msg-name {:keys [query] :as msg}]
  (case msg-name
    :fulcro.inspect.client/network-request
    (respond responses msg (parser {} query))

    (js/console.warn "No implementation for msg " msg-name msg)))

(ws/defcard index-explorer-panel-card
  {::wsm/align ::wsm/stretch-flex}
  (let [parser     (ui-parser/parser)
        responses* (atom {})]
    (ct.fulcro/fulcro-card
      {::f.portal/root fi.iex/IndexExplorer
       ::f.portal/app  {:networking (f.network/pathom-remote
                                      (fn [env tx]
                                        (parser (assoc env
                                                  :responses* responses*
                                                  :send-message #(send-message responses* % %2)) tx)))}})))
