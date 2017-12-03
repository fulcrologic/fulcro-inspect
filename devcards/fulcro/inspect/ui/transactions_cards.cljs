(ns fulcro.inspect.ui.transactions-cards
  (:require
    [devcards.core :refer-macros [defcard]]
    [fulcro-css.css :as css]
    [fulcro.client.cards :refer-macros [defcard-fulcro]]
    [fulcro.inspect.ui.transactions :as transactions]
    [fulcro.inspect.card-helpers :as card-helpers]
    [fulcro.client.primitives :as fp]))

(def tx
  '{:tx        [(fulcro.client.mutations/set-props
                  {:fulcro.inspect.ui.data-viewer/expanded {[] true [:foo] true}})]
    :ret       {fulcro.client.mutations/set-props
                {:result
                 {:fulcro.inspect.core/app-id
                                  :fulcro.inspect.ui.data-viewer-cards/data-viewer-16
                  :ui/react-key   "5537b4a1-589d-40d2-a98f-509c918f222e"
                  :ui/root
                                  [:fulcro.inspect.ui.data-viewer/id
                                   #uuid "f13d5cb1-82c8-48fa-abc6-73b54dbf33f9"]
                  :fulcro.inspect.ui.data-viewer/id
                                  {#uuid "f13d5cb1-82c8-48fa-abc6-73b54dbf33f9"
                                   {:fulcro.inspect.ui.data-viewer/id
                                                                            #uuid "f13d5cb1-82c8-48fa-abc6-73b54dbf33f9"
                                    :fulcro.inspect.ui.data-viewer/content
                                                                            {:a 3 :b 10 :foo {:barr ["baz" "there"]}}
                                    :fulcro.inspect.ui.data-viewer/expanded {[] true [:foo] true}}}
                  :fulcro.client.primitives/tables #{:fulcro.inspect.ui.data-viewer/id}
                  :ui/locale      :en}}}
    :old-state {:id {123 {:a 1}}}
    :new-state {:id {123 {:b 2}}}
    :sends     {}
    :ref       [:fulcro.inspect.ui.data-viewer/id #uuid "f13d5cb1-82c8-48fa-abc6-73b54dbf33f9"]
    :component nil})

(def TxRoot (card-helpers/make-root transactions/Transaction ::single-tx))

(defcard-fulcro transaction
  TxRoot
  (card-helpers/init-state-atom TxRoot tx))

(def TxListRoot (card-helpers/make-root transactions/TransactionList ::tx-list))

(defcard-fulcro transaction-list
  TxListRoot
  {}
  {:fulcro {:started-callback
            (fn [{:keys [reconciler]}]
              (let [ref (-> reconciler fp/app-state deref :ui/root)]
                (doseq [x (repeat 5 tx)]
                  (fp/transact! reconciler ref [`(transactions/add-tx ~x)]))))}})

(defcard-fulcro transaction-list-empty
  transactions/TransactionList)

(css/upsert-css "transaction" transactions/TransactionList)
