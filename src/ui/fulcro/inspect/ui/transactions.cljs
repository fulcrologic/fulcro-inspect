(ns fulcro.inspect.ui.transactions
  (:require
    [clojure.data :as data]
    [clojure.string :as str]
    [fulcro-css.css :as css]
    [fulcro.client.mutations :as mutations :refer-macros [defmutation]]
    [fulcro.inspect.helpers :as h]
    [fulcro.inspect.ui.core :as ui :refer [colors]]
    [fulcro.inspect.ui.data-viewer :as data-viewer]
    [fulcro.client.localized-dom :as dom]
    [fulcro.client.primitives :as fp]
    [fulcro.inspect.helpers :as db.h]))

(fp/defsc TransactionRow
  [this
   {:ui/keys             [tx-row-view]
    :fulcro.history/keys [client-time]
    :as                  props}
   {::keys [on-select selected? on-replay]}]

  {:initial-state (fn [{:fulcro.history/keys [tx] :as transaction}]
                    (merge {::tx-id                     (random-uuid)
                            :fulcro.history/client-time (js/Date.)
                            :ui/tx-row-view             (fp/get-initial-state data-viewer/DataViewer tx)}
                           transaction))

   :ident         [::tx-id ::tx-id]
   :query         [::tx-id
                   :fulcro.history/client-time :fulcro.history/tx
                   :fulcro.history/db-before :fulcro.history/db-after
                   :fulcro.history/network-sends :ident-ref :component
                   {:ui/tx-row-view (fp/get-query data-viewer/DataViewer)}]
   :css           [[:.container {:display       "flex"
                                 :cursor        "pointer"
                                 :flex          "1"
                                 :border-bottom (str "1px solid " (:bg-light-border colors))
                                 :align-items   "center"
                                 :padding       "5px 0"
                                 :color         (:text colors)}
                    [:.icon {:display "none"}]
                    [:&:hover {:background (:row-hover colors)}
                     [:.icon {:display "block"}]]
                    [:&.selected {:background (:row-selected colors)}]]

                   [:.data-container {:flex 1}]
                   [:.icon {:margin "-5px 6px"}
                    [:$c-icon {:fill      (:icon-normal colors)
                               :transform "scale(0.7)"}
                     [:&:hover {:fill (:icon-strong colors)}]]]
                   [:.timestamp ui/css-timestamp]]
   :css-include   [data-viewer/DataViewer]}

  (dom/div :.container {:classes [(if selected? :.selected)]
                        :onClick #(if on-select (on-select props))}
    (dom/div :.timestamp (ui/print-timestamp client-time))
    (dom/div :.data-container
      (data-viewer/data-viewer (assoc tx-row-view ::data-viewer/static? true)))
    (if on-replay
      (dom/div :.icon {:onClick #(do
                                   (.stopPropagation %)
                                   (on-replay props))}
        (ui/icon {:title "Replay mutation"} :refresh)))))

(let [factory (fp/factory TransactionRow {:keyfn ::tx-id})]
  (defn transaction-row [props computed]
    (factory (fp/computed props computed))))

(fp/defsc Transaction
  [this {:keys                [ident-ref component]
         :fulcro.history/keys [network-sends]
         :ui/keys             [tx-view sends-view
                               old-state-view new-state-view
                               diff-add-view diff-rem-view]}]
  {:initial-state
   (fn [{:fulcro.history/keys [tx network-sends db-before db-after]
         :as                  transaction}]
     (let [[add rem] (data/diff db-after db-before)]
       (merge {::tx-id (random-uuid)}
              transaction
              {:ui/tx-view        (-> (fp/get-initial-state data-viewer/DataViewer tx)
                                      (assoc ::data-viewer/expanded {[] true}))
               :ui/sends-view     (fp/get-initial-state data-viewer/DataViewer network-sends)
               :ui/old-state-view (fp/get-initial-state data-viewer/DataViewer db-before)
               :ui/new-state-view (fp/get-initial-state data-viewer/DataViewer db-after)
               :ui/diff-add-view  (fp/get-initial-state data-viewer/DataViewer add)
               :ui/diff-rem-view  (fp/get-initial-state data-viewer/DataViewer rem)
               :ui/full-computed? true})))

   :ident
   [::tx-id ::tx-id]

   :query
   [::tx-id ::timestamp
    :fulcro.history/client-time :fulcro.history/tx
    :fulcro.history/db-before :fulcro.history/db-after
    :fulcro.history/network-sends :ident-ref :component
    :ui/full-computed?
    {:ui/tx-view (fp/get-query data-viewer/DataViewer)}
    {:ui/tx-row-view (fp/get-query data-viewer/DataViewer)}
    {:ui/sends-view (fp/get-query data-viewer/DataViewer)}
    {:ui/old-state-view (fp/get-query data-viewer/DataViewer)}
    {:ui/new-state-view (fp/get-query data-viewer/DataViewer)}
    {:ui/diff-add-view (fp/get-query data-viewer/DataViewer)}
    {:ui/diff-rem-view (fp/get-query data-viewer/DataViewer)}]

   :css
   [[:.container {:height "100%"}]]

   :css-include
   [data-viewer/DataViewer]}
  (let [css (css/get-classnames Transaction)]
    (dom/div #js {:className (:container css)}
      (ui/info {::ui/title "Ref"}
        (ui/ident {} ident-ref))

      (ui/info {::ui/title "Transaction"}
        (data-viewer/data-viewer tx-view))

      (if (seq network-sends)
        (ui/info {::ui/title "Sends"}
          (data-viewer/data-viewer sends-view)))

      (ui/info {::ui/title "Diff added"}
        (data-viewer/data-viewer diff-add-view))

      (ui/info {::ui/title "Diff removed"}
        (data-viewer/data-viewer diff-rem-view))

      (if component
        (ui/info {::ui/title "Component"}
          (ui/comp-display-name {}
            (str component))))

      (ui/info {::ui/title "State before"}
        (data-viewer/data-viewer old-state-view))

      (ui/info {::ui/title "State after"}
        (data-viewer/data-viewer new-state-view)))))

(def transaction (fp/factory Transaction {:keyfn ::tx-id}))

(defmutation add-tx [tx]
  (action [env]
    (h/create-entity! env TransactionRow tx :append ::tx-list)
    (h/swap-entity! env update ::tx-list #(->> (take-last 100 %) vec))))

(defmutation select-tx [tx]
  (action [env]
    (let [{:keys [state ref]} env
          tx-ref (fp/ident Transaction tx)
          {:ui/keys [full-computed?]
           :as      transaction} (fp/db->tree (fp/get-query TransactionRow) (get-in @state tx-ref) @state)]
      (if-not full-computed?
        (swap! state h/merge-entity Transaction
          (fp/get-initial-state Transaction transaction)))
      (swap! state update-in ref assoc ::active-tx tx-ref))))

(defmutation clear-transactions [_]
  (action [env]
    (let [{:keys [state ref]} env
          tx-refs (get-in @state (conj ref ::tx-list))]
      (swap! state update-in ref assoc ::tx-list [] ::active-tx nil)
      (if (seq tx-refs)
        (swap! state #(reduce h/deep-remove-ref % tx-refs))))))

(defmutation replay-tx [_]
  (remote [env]
    (db.h/remote-mutation env 'transact)))

(fp/defsc TransactionList
  [this
   {::keys [tx-list active-tx tx-filter]}]
  {:initial-state (fn [_] {::tx-list-id (random-uuid)
                           ::tx-list    []
                           ::tx-filter  ""})
   :ident         [::tx-list-id ::tx-list-id]
   :query         [::tx-list-id ::tx-filter
                   {::active-tx (fp/get-query Transaction)}
                   {::tx-list (fp/get-query TransactionRow)}]
   :css           [[:.container {:display        "flex"
                                 :width          "100%"
                                 :flex           "1"
                                 :flex-direction "column"
                                 :colors         (:text colors)}]

                   [:.transactions {:flex     "1"
                                    :overflow "auto"}]]
   :css-include   [Transaction TransactionRow ui/CSS]}

  (let [tx-list (if (seq tx-filter)
                  (filterv #(str/includes? (-> % :tx pr-str) tx-filter) tx-list)
                  tx-list)]
    (dom/div :.container
      (ui/toolbar {}
        (ui/toolbar-action {:onClick #(fp/transact! this [`(clear-transactions {})])}
          (ui/icon {:title "Clear transactions"} :do_not_disturb))
        (ui/toolbar-separator)
        (ui/toolbar-text-field {:placeholder "Filter"
                                :value       tx-filter
                                :onChange    #(mutations/set-string! this ::tx-filter :event %)}))
      (dom/div :.transactions
        (if (seq tx-list)
          (->> tx-list
               rseq
               (mapv #(transaction-row %
                        {::on-select
                         (fn [tx]
                           (fp/transact! this [`(select-tx ~tx)]))

                         ::on-replay
                         (fn [{:keys [tx ident-ref]}]
                           (fp/transact! this [`(replay-tx ~{:tx tx :tx-ref ident-ref})]))

                         ::selected?
                         (= (::tx-id active-tx) (::tx-id %))})))))
      (if active-tx
        (ui/focus-panel {}
          (ui/toolbar {::ui/classes [:details]}
            (ui/toolbar-spacer)
            (ui/toolbar-action {:onClick #(mutations/set-value! this ::active-tx nil)}
              (ui/icon {:title "Close panel"} :clear)))
          (ui/focus-panel-content {}
            (transaction active-tx)))))))

(def transaction-list (fp/factory TransactionList {:keyfn ::tx-list-id}))
