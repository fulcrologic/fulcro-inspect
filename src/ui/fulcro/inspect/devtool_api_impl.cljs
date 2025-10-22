(ns fulcro.inspect.devtool-api-impl
  (:require
    [com.fulcrologic.devtools.common.built-in-mutations :as bi]
    [com.fulcrologic.devtools.common.protocols :as dp]
    [com.fulcrologic.devtools.common.resolvers :as dres]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as fp]
    [com.fulcrologic.fulcro.inspect.devtool-api :as dapi]
    [com.wsscode.pathom.connect :as pc]
    [fulcro.inspect.lib.history :as hist]
    [fulcro.inspect.ui.inspector :as inspector]
    [fulcro.inspect.ui.multi-inspector :as multi-inspector]
    [fulcro.inspect.ui.network :as network]
    [taoensso.timbre :as log]))

(dres/defmutation app-started [{:fulcro/keys [app]} params]
  {::pc/sym `dapi/app-started}
  (log/info "App started event received for target:" (::app/id params))
  (fp/transact! app `[(fulcro.inspect.common/start-app ~params)])
  nil)

(defn dispose-app [app {::app/keys [id] :as params}]
  (fp/transact! app [(hist/clear-history params)])
  (fp/transact! app [(multi-inspector/remove-inspector params)]))

(dres/defmutation connect-mutation [{:fulcro/keys [app] :devtool/keys [connection]} {:keys [connected? target-id] :as params}]
  {::pc/sym `bi/devtool-connected}
  (cond
    ;; Handle connection events (new target connecting)
    (and target-id connected?)
    (do
      (log/info "Target connecting:" target-id "- sending acknowledgment")
      ;; WORKAROUND: Send a message back to the target to trigger its connected mutation
      ;; This is needed because fulcro-devtools-remote doesn't send a response when detecting new targets
      (when connection
        (dp/transmit! connection target-id [(bi/devtool-connected {:connected? true :target-id target-id})]))
      nil)

    ;; Handle disconnection for specific target
    (and target-id (not connected?))
    (do
      (log/info "Target disconnecting:" target-id)
      (dispose-app app {::app/id target-id}))

    ;; Handle disconnection of all targets (e.g., page reload or devtool closed)
    (not connected?)
    (do
      (log/info "All targets disconnecting")
      (fp/transact! app [(multi-inspector/remove-all-inspectors {})]))))

(dres/defmutation send-started [{:fulcro/keys [app]} params]
  {::pc/sym `dapi/send-started}
  (fp/transact! app [(network/request-start params)]
    {:ref [:network-history/id [:x (::app/id params)]]})
  nil)

(dres/defmutation send-finished [{:fulcro/keys [app]} params]
  {::pc/sym `dapi/send-finished}
  (fp/transact! app [(network/request-finish params)]
    {:ref [:network-history/id [:x (::app/id params)]]})
  nil)

(dres/defmutation send-failed [{:fulcro/keys [app]} params]
  {::pc/sym `dapi/send-failed}
  (fp/transact! app [(network/request-finish params)]
    {:ref [:network-history/id [:x (::app/id params)]]})
  nil)

(defn new-client-tx [inspector {::app/keys [id]
                                :as        txn}]
  (fp/transact! inspector
    `[(fulcro.inspect.ui.transactions/add-tx ~txn)]
    {:ref [:fulcro.inspect.ui.transactions/tx-list-id [:x id]]}))

(dres/defmutation optimistic-action [{:fulcro/keys [app]} params]
  {::pc/sym `dapi/optimistic-action}
  (new-client-tx app params)
  nil)

(dres/defmutation update-client-db [{:fulcro/keys [app]} {::app/keys    [id]
                                                          :history/keys [version value] :as history-step}]
  {::pc/sym `dapi/db-changed}
  (fp/transact! app [(hist/save-history-step history-step)])
  nil)

(dres/defmutation focus-target [{:fulcro/keys [app]} {::app/keys [id]}]
  {::pc/sym `dapi/focus-target}
  (log/info "Asked to focus" id)
  (when id
    (fp/transact! app [(multi-inspector/set-app {::inspector/id [:x id]})]
      {:ref multi-inspector/multi-inspector-ident}))
  nil)
