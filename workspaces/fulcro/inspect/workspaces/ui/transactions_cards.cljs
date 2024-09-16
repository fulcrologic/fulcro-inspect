(ns fulcro.inspect.workspaces.ui.transactions-cards
  (:require [nubank.workspaces.lib.fulcro-portal :as f.portal]
            [nubank.workspaces.card-types.fulcro :as ct.fulcro]
            [nubank.workspaces.card-types.react :as ct.react]
            [nubank.workspaces.core :as ws]
            [nubank.workspaces.model :as wsm]
            [fulcro.inspect.ui.transactions :as c.tx]
            [com.fulcrologic.fulcro-css.localized-dom :as dom]
            [com.fulcrologic.fulcro.components :as fp]))

(defn new-tx [props]
  (merge {::tx-id                       (random-uuid)
          :fulcro.history/client-time   (js/Date.)
          :fulcro.history/tx            '(some-tx {})
          :fulcro.history/db-before     {}
          :fulcro.history/db-after      {}
          :fulcro.history/network-sends []
          :ident-ref                    [:ident 42]
          :component                    nil}
    props))

(def demo-tx-list
  {::c.tx/tx-list-id "transactions"
   ::c.tx/tx-list    [(new-tx {})]})

(fp/defsc TransactionDemo
  [this {::keys [tx-list]}]
  {:pre-merge (fn [{:keys [current-normalized data-tree]}]
                (merge {::id      (random-uuid)
                        ::tx-list {}}
                  current-normalized data-tree))
   :ident     [::id ::id]
   :query     [::id
               {::tx-list (fp/get-query c.tx/TransactionList)}]
   :css       [[:.container {:display        "flex"
                             :flex           "1"
                             :flex-direction "column"}]]}
  (dom/div :.container
    (dom/div
      (dom/button {:onClick #(fp/transact! (fp/get-reconciler this) (fp/get-ident c.tx/TransactionList tx-list)
                               `[(c.tx/add-tx ~(new-tx {}))])}
        "Add tx")
      (dom/button {:onClick #(fp/transact! (fp/get-reconciler this) (fp/get-ident c.tx/TransactionList tx-list)
                               `[(c.tx/add-tx ~(new-tx {c.tx/tx-options {:compressible? true}}))])}
        "Add compressible tx")
      (dom/button {:onClick #(fp/transact! (fp/get-reconciler this) (fp/get-ident c.tx/TransactionList tx-list)
                               `[(c.tx/add-tx ~(new-tx {c.tx/tx-options    {:compressible? true}
                                                        :fulcro.history/tx (list 'other-tx {:x (rand-int 50)})}))])}
        "Add compressible tx 2"))
    (c.tx/transaction-list tx-list)))

(ws/defcard transactions-demo-card
  {::wsm/align ::wsm/stretch-flex}
  (ct.fulcro/fulcro-card
    {::f.portal/root          TransactionDemo
     ::f.portal/initial-state (fn [_] {::tx-list demo-tx-list})}))
