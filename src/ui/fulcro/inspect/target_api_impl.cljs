(ns fulcro.inspect.target-api-impl
  (:require
    [com.fulcrologic.devtools.common.built-in-mutations :as bi]
    [com.fulcrologic.devtools.common.resolvers :as dres]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as fp]
    [com.fulcrologic.fulcro.inspect.devtool-api :as tapi]
    [com.wsscode.pathom.connect :as pc]
    [fulcro.inspect.lib.history :as hist]
    [fulcro.inspect.ui.multi-inspector :as multi-inspector]
    [fulcro.inspect.ui.network :as network]))

(dres/defmutation app-started [{:fulcro/keys [app]} params]
  {::pc/sym `tapi/app-started}
  (fp/transact! app `[(fulcro.inspect.common/start-app ~params)])
  nil)

(defn dispose-app [app {::app/keys [id] :as params}]
  (fp/transact! app [(hist/clear-history params)])
  (fp/transact! app [(multi-inspector/remove-inspector params)]))

(dres/defmutation connect-mutation [{:fulcro/keys [app]} {:keys [connected? target-id] :as params}]
  {::pc/sym `bi/devtool-connected}
  (cond
    (and target-id (not connected?)) (dispose-app app {::app/id target-id})
    (not connected?) (fp/transact! app [(multi-inspector/remove-all-inspectors {})])))

(dres/defmutation send-started [{:fulcro/keys [app]} params]
  {::pc/sym `tapi/send-started}
  (fp/transact! app [(network/request-start params)]
    {:ref [:network-history/id [:x (::app/id params)]]})
  nil)

(dres/defmutation send-finished [{:fulcro/keys [app]} params]
  {::pc/sym `tapi/send-finished}
  (fp/transact! app [(network/request-finish params)]
    {:ref [:network-history/id [:x (::app/id params)]]})
  nil)

(dres/defmutation send-failed [{:fulcro/keys [app]} params]
  {::pc/sym `tapi/send-failed}
  (fp/transact! app [(network/request-finish params)]
    {:ref [:network-history/id [:x (::app/id params)]]})
  nil)

(defn new-client-tx [inspector {::app/keys [id]
                                :as        txn}]
  (fp/transact! inspector
    `[(fulcro.inspect.ui.transactions/add-tx ~txn)]
    {:ref [:fulcro.inspect.ui.transactions/tx-list-id [:x id]]}))

(dres/defmutation optimistic-action [{:fulcro/keys [app]} params]
  {::pc/sym `tapi/optimistic-action}
  (new-client-tx app params)
  nil)

(dres/defmutation update-client-db [{:fulcro/keys [app]} {::app/keys    [id]
                                                          :history/keys [version value] :as history-step}]
  {::pc/sym `tapi/db-changed}
  (fp/transact! app [(hist/save-history-step history-step)])
  nil)
