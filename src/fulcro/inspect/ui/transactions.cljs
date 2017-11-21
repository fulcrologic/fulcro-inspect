(ns fulcro.inspect.ui.transactions
  (:require
    [fulcro.inspect.ui.data-viewer :as data-viewer]
    [fulcro.inspect.helpers :as h]
    [fulcro.client.core :as fulcro]
    [fulcro.client.mutations :as mutations :refer-macros [defmutation]]
    [fulcro-css.css :as css]
    [om.dom :as dom]
    [om.next :as om]
    [fulcro.inspect.ui.core :as ui]))

(defn add-zeros [n x]
  (loop [n (str n)]
    (if (< (count n) x)
      (recur (str 0 n))
      n)))

(defn print-timestamp [date]
  (str (add-zeros (.getHours date) 2) ":"
       (add-zeros (.getMinutes date) 2) ":"
       (add-zeros (.getSeconds date) 2) ":"
       (add-zeros (.getMilliseconds date) 3)))

(om/defui ^:once TransactionRow
  static fulcro/InitialAppState
  (initial-state [_ {:keys [tx] :as transaction}]
    (merge {::tx-id     (random-uuid)
            ::timestamp (js/Date.)
            :ui/tx-view (assoc (fulcro/get-initial-state data-viewer/DataViewer tx)
                          ::data-viewer/expanded {})}
           transaction))

  static om/IQuery
  (query [_]
    [::tx-id ::timestamp :ref
     {:ui/tx-view (om/get-query data-viewer/DataViewer)}])

  static om/Ident
  (ident [_ props] [::tx-id (::tx-id props)])

  static css/CSS
  (local-rules [_] [[:.container {:display       "flex"
                                  :flex          "1"
                                  :border-bottom "1px solid #eee"
                                  :padding       "5px 0"}]
                    [:.ident {:font-family ui/label-font-family
                              :font-size   ui/label-font-size
                              :align-self  "flex-end"
                              :padding     "3px 6px"
                              :background  "#f3f3f3"
                              :color       "#424242"}]
                    [:.timestamp {:font-family "monospace"
                                  :font-size   "11px"
                                  :color       "#808080"
                                  :margin      "0 4px 0 7px"}]])
  (include-children [_] [data-viewer/DataViewer])

  Object
  (render [this]
    (let [{:ui/keys [tx-view]
           ::keys   [timestamp]} (om/props this)
          css (css/get-classnames TransactionRow)]
      (dom/div #js {:className (:container css)}
        (dom/div #js {:className (:timestamp css)} (print-timestamp timestamp))
        (data-viewer/data-viewer tx-view)))))

(def transaction-row (om/factory TransactionRow))

(om/defui ^:once Transaction
  static fulcro/InitialAppState
  (initial-state [_ {:keys [tx ret] :as transaction}]
    (merge {::tx-id      (random-uuid)
            ::timestamp  (js/Date.)
            :ui/tx-view  (assoc (fulcro/get-initial-state data-viewer/DataViewer tx)
                           ::data-viewer/expanded {})
            :ui/ret-view (assoc (fulcro/get-initial-state data-viewer/DataViewer ret)
                           ::data-viewer/expanded {})}
           transaction))

  static om/IQuery
  (query [_]
    [::tx-id ::timestamp :tx :ret :sends :old-state :new-state :ref :component
     {:ui/tx-view (om/get-query data-viewer/DataViewer)}
     {:ui/ret-view (om/get-query data-viewer/DataViewer)}])

  static om/Ident
  (ident [_ props] [::tx-id (::tx-id props)])

  static css/CSS
  (local-rules [_] [[:.container {:display "flex"
                                  :flex    "1"
                                  :padding "5px 0"}]
                    [:.ident {:align-self  "flex-end"
                              :padding     "3px 6px"
                              :background  "#f3f3f3"
                              :color       "#424242"
                              :font-family ui/label-font-family
                              :font-size   ui/label-font-size}]
                    [:.timestamp {:font-family "monospace"
                                  :font-size   "11px"
                                  :color       "#808080"
                                  :margin      "0 4px 0 7px"}]])
  (include-children [_] [data-viewer/DataViewer])

  Object
  (render [this]
    (let [{:keys    [tx ret sends old-state new-state ref component]
           :ui/keys [tx-view ret-view]
           ::keys   [timestamp]} (om/props this)
          css (css/get-classnames Transaction)]
      (dom/div #js {:className (:container css)}
        (data-viewer/data-viewer tx-view)
        (data-viewer/data-viewer ret-view)))))

(def transaction (om/factory Transaction))

(defmutation add-tx [tx]
  (action [env]
    (h/create-entity! env Transaction tx :append ::tx-list)))

(om/defui ^:once TransactionList
  static fulcro/InitialAppState
  (initial-state [_ tx-list]
    {::tx-list (mapv #(fulcro/get-initial-state Transaction %) tx-list)})

  static om/IQuery
  (query [_] [::tx-list-id
              {::active-tx (om/get-query Transaction)}
              {::tx-list (om/get-query TransactionRow)}])

  static om/Ident
  (ident [_ props] [::tx-list-id (::tx-list-id props)])

  static css/CSS
  (local-rules [_] [[:.container {:display        "flex"
                                  :flex           "1"
                                  :flex-direction "column"}]
                    [:.tools {:border-bottom "1px solid #dadada"
                              :font-family   ui/label-font-family
                              :font-size     ui/label-font-size}]
                    [:.transactions {"flex" "1"}]])
  (include-children [_] [Transaction TransactionRow])

  Object
  (render [this]
    (let [{::keys [tx-list active-tx]} (om/props this)
          css (css/get-classnames TransactionList)]
      (dom/div #js {:className (:container css)}
        (dom/div #js {:className (:tools css)}
          "tools")
        (dom/div #js {:className (:transactions css)}
          (if (seq tx-list)
            (mapv transaction-row tx-list)
            "No transactions"))
        (if active-tx
          (transaction active-tx))))))

(def transaction-list (om/factory TransactionList))
