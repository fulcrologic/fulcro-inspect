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
    (merge {::tx-id         (random-uuid)
            ::timestamp     (js/Date.)
            :ui/tx-row-view (assoc (fulcro/get-initial-state data-viewer/DataViewer tx)
                              ::data-viewer/expanded {})}
           transaction))

  static om/IQuery
  (query [_]
    [::tx-id ::timestamp :ref
     {:ui/tx-row-view (om/get-query data-viewer/DataViewer)}])

  static om/Ident
  (ident [_ props] [::tx-id (::tx-id props)])

  static css/CSS
  (local-rules [_] [[:.container {:display       "flex"
                                  :cursor        "pointer"
                                  :flex          "1"
                                  :border-bottom "1px solid #eee"
                                  :padding       "5px 0"}
                     [:&:hover {:background "#eef3fa"}]
                     [:&.selected {:background "#e6e6e6"}]]

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
    (let [{:ui/keys [tx-row-view]
           ::keys   [timestamp]
           :as      props} (om/props this)
          {::keys [on-select selected?]} (om/get-computed props)
          css (css/get-classnames TransactionRow)]
      (dom/div #js {:className (cond-> (:container css)
                                 selected? (str " " (:selected css)))
                    :onClick   #(if on-select (on-select props))}
        (dom/div #js {:className (:timestamp css)} (print-timestamp timestamp))
        (data-viewer/data-viewer (assoc tx-row-view ::data-viewer/static? true))))))

(let [factory (om/factory TransactionRow)]
  (defn transaction-row [props computed]
    (factory (om/computed props computed))))

(om/defui ^:once Transaction
  static fulcro/InitialAppState
  (initial-state [_ {:keys [tx ret] :as transaction}]
    (merge {::tx-id         (random-uuid)
            ::timestamp     (js/Date.)
            :ui/tx-view     (assoc (fulcro/get-initial-state data-viewer/DataViewer tx)
                              ::data-viewer/expanded {})
            :ui/tx-row-view (assoc (fulcro/get-initial-state data-viewer/DataViewer tx)
                              ::data-viewer/expanded {})
            :ui/ret-view    (assoc (fulcro/get-initial-state data-viewer/DataViewer ret)
                              ::data-viewer/expanded {})}
           transaction))

  static om/IQuery
  (query [_]
    [::tx-id ::timestamp :tx :ret :sends :old-state :new-state :ref :component
     {:ui/tx-view (om/get-query data-viewer/DataViewer)}
     {:ui/ret-view (om/get-query data-viewer/DataViewer)}
     {:ui/tx-row-view (om/get-query data-viewer/DataViewer)}])

  static om/Ident
  (ident [_ props] [::tx-id (::tx-id props)])

  static css/CSS
  (local-rules [_] [[:.container {:height     "50%"
                                  :padding    "5px 0"
                                  :overflow   "auto"
                                  :border-top "1px solid #a3a3a3"}]
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

(defmutation select-tx [tx]
  (action [env]
    (let [{:keys [state ref]} env
          tx-ref (om/ident Transaction tx)]
      (swap! state update-in ref assoc ::active-tx tx-ref))))

(defmutation clear-transactions [_]
  (action [env]
    (let [{:keys [state ref]} env
          tx-refs (get-in @state (conj ref ::tx-list))]
      (swap! state assoc-in (conj ref ::tx-list) [])
      (if (seq tx-refs)
        (swap! state #(reduce h/deep-remove-ref % tx-refs))))))

(om/defui ^:once TransactionList
  static fulcro/InitialAppState
  (initial-state [_ _]
    {::tx-list-id (random-uuid)
     ::tx-list    []})

  static om/IQuery
  (query [_] [::tx-list-id
              {::active-tx (om/get-query Transaction)}
              {::tx-list (om/get-query TransactionRow)}])

  static om/Ident
  (ident [_ props] [::tx-list-id (::tx-list-id props)])

  static css/CSS
  (local-rules [_] [[:.container {:display        "flex"
                                  :height         "100%"
                                  :flex-direction "column"}]
                    [:.tools {:border-bottom "1px solid #dadada"
                              :font-family   ui/label-font-family
                              :font-size     ui/label-font-size
                              :display       "flex"
                              :align-items   "center"}
                     [:.icon {:padding     "1px 7px"
                              :font-size   "16px"
                              :cursor      "pointer"
                              :color       "transparent"
                              :text-shadow (str "0 0 0 " ui/color-text-normal)}
                      [:&:hover {:text-shadow (str "0 0 0 " ui/color-text-strong)}]]]
                    [:.transactions {:flex     "1"
                                     :overflow "auto"}]])
  (include-children [_] [Transaction TransactionRow])

  Object
  (render [this]
    (let [{::keys [tx-list active-tx]} (om/props this)
          css (css/get-classnames TransactionList)]
      (dom/div #js {:className (:container css)}
        (dom/div #js {:className (:tools css)}
          (dom/div #js {:className (:icon css)
                        :title     "Clear transactions"
                        :onClick   #(om/transact! this [`(clear-transactions {})])}
            "🚫"))
        (dom/div #js {:className (:transactions css)}
          (if (seq tx-list)
            (mapv #(transaction-row %
                     {::on-select
                      (fn [tx]
                        (om/transact! this [`(select-tx ~tx)]))

                      ::selected?
                      (= (::tx-id active-tx) (::tx-id %))})
              tx-list)
            "No transactions"))
        (if active-tx
          (transaction active-tx))))))

(def transaction-list (om/factory TransactionList))
