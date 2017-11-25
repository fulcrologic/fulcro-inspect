(ns fulcro.inspect.ui.transactions
  (:require
    [clojure.data :as data]
    [clojure.string :as str]
    [fulcro-css.css :as css]
    [fulcro.client.core :as fulcro]
    [fulcro.client.mutations :as mutations :refer-macros [defmutation]]
    [fulcro.inspect.helpers :as h]
    [fulcro.inspect.ui.core :as ui]
    [fulcro.inspect.ui.data-viewer :as data-viewer]
    [goog.object :as gobj]
    [om.dom :as dom]
    [om.next :as om]))

(om/defui ^:once TransactionRow
  static fulcro/InitialAppState
  (initial-state [_ {:keys [tx] :as transaction}]
    (merge {::tx-id         (random-uuid)
            ::timestamp     (js/Date.)
            :ui/tx-row-view (fulcro/get-initial-state data-viewer/DataViewer tx)}
           transaction))

  static om/Ident
  (ident [_ props] [::tx-id (::tx-id props)])

  static om/IQuery
  (query [_]
    [::tx-id ::timestamp :ref :tx
     {:ui/tx-row-view (om/get-query data-viewer/DataViewer)}])

  static css/CSS
  (local-rules [_] [[:.container {:display       "flex"
                                  :cursor        "pointer"
                                  :flex          "1"
                                  :border-bottom "1px solid #eee"
                                  :padding       "5px 0"}
                     [:&:hover {:background ui/color-row-hover}]
                     [:&.selected {:background ui/color-row-selected}]]

                    [:.ident {:font-family ui/label-font-family
                              :font-size   ui/label-font-size
                              :align-self  "flex-end"
                              :padding     "3px 6px"
                              :background  "#f3f3f3"
                              :color       "#424242"}]
                    [:.timestamp ui/css-timestamp]])
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
        (dom/div #js {:className (:timestamp css)} (ui/print-timestamp timestamp))
        (data-viewer/data-viewer (assoc tx-row-view ::data-viewer/static? true))))))

(let [factory (om/factory TransactionRow)]
  (defn transaction-row [props computed]
    (factory (om/computed props computed))))

(om/defui ^:once Transaction
  static fulcro/InitialAppState
  (initial-state [_ {:keys [tx ret sends old-state new-state] :as transaction}]
    (merge {::tx-id            (random-uuid)
            ::timestamp        (js/Date.)
            :ui/tx-view        (-> (fulcro/get-initial-state data-viewer/DataViewer tx)
                                   (assoc ::data-viewer/expanded {[] true}))
            :ui/tx-row-view    (fulcro/get-initial-state data-viewer/DataViewer tx)
            :ui/ret-view       (fulcro/get-initial-state data-viewer/DataViewer ret)
            :ui/sends-view     (fulcro/get-initial-state data-viewer/DataViewer sends)
            :ui/old-state-view (fulcro/get-initial-state data-viewer/DataViewer old-state)
            :ui/new-state-view (fulcro/get-initial-state data-viewer/DataViewer new-state)}
           transaction))

  static om/Ident
  (ident [_ props] [::tx-id (::tx-id props)])

  static om/IQuery
  (query [_]
    [::tx-id ::timestamp :tx :ret :sends :old-state :new-state :ref :component
     {:ui/tx-view (om/get-query data-viewer/DataViewer)}
     {:ui/ret-view (om/get-query data-viewer/DataViewer)}
     {:ui/tx-row-view (om/get-query data-viewer/DataViewer)}
     {:ui/sends-view (om/get-query data-viewer/DataViewer)}
     {:ui/old-state-view (om/get-query data-viewer/DataViewer)}
     {:ui/new-state-view (om/get-query data-viewer/DataViewer)}
     {:ui/diff-add-view (om/get-query data-viewer/DataViewer)}
     {:ui/diff-rem-view (om/get-query data-viewer/DataViewer)}])

  static css/CSS
  (local-rules [_] [[:.container {:height "100%"}]
                    [:.ident {:align-self  "flex-end"
                              :padding     "5px 6px"
                              :background  "#f3f3f3"
                              :color       "#424242"
                              :display     "inline-block"
                              :font-family ui/mono-font-family
                              :font-size   ui/label-font-size}]
                    [:.group ui/css-info-group]
                    [:.label ui/css-info-label]])
  (include-children [_] [data-viewer/DataViewer])

  Object
  (render [this]
    (let [{:keys    [sends ref component]
           :ui/keys [tx-view ret-view sends-view
                     old-state-view new-state-view
                     diff-add-view diff-rem-view]
           :as      props} (om/props this)
          css (css/get-classnames Transaction)]
      (dom/div #js {:className (:container css)}
        (dom/div #js {:className (:group css)}
          (dom/div #js {:className (:label css)} "Ref")
          (dom/div #js {:className (:ident css)} (pr-str ref)))

        (dom/div #js {:className (:group css)}
          (dom/div #js {:className (:label css)} "Transaction")
          (data-viewer/data-viewer tx-view))

        (dom/div #js {:className (:group css)}
          (dom/div #js {:className (:label css)} "Response")
          (data-viewer/data-viewer ret-view))

        (if (seq sends)
          (dom/div #js {:className (:group css)}
            (dom/div #js {:className (:label css)} "Sends")
            (data-viewer/data-viewer sends-view)))

        (dom/div #js {:className (:group css)}
          (dom/div #js {:className (:label css)} "Diff added")
          (data-viewer/data-viewer diff-add-view))

        (dom/div #js {:className (:group css)}
          (dom/div #js {:className (:label css)} "Diff removed")
          (data-viewer/data-viewer diff-rem-view))

        (if component
          (dom/div #js {:className (:group css)}
            (dom/div #js {:className (:label css)} "Component")
            (dom/div #js {:className (:ident css)}
              (gobj/get (om/react-type component) "displayName"))))

        (dom/div #js {:className (:group css)}
          (dom/div #js {:className (:label css)} "State before")
          (data-viewer/data-viewer old-state-view))

        (dom/div #js {:className (:group css)}
          (dom/div #js {:className (:label css)} "State after")
          (data-viewer/data-viewer new-state-view))))))

(def transaction (om/factory Transaction))

(defmutation add-tx [tx]
  (action [env]
    (h/create-entity! env Transaction tx :append ::tx-list)
    (h/swap-entity! env update ::tx-list #(->> (take-last 100 %) vec))))

(defmutation select-tx [tx]
  (action [env]
    (let [{:keys [state ref] :as env} env
          tx-ref (om/ident Transaction tx)
          {:keys [ui/diff-computed? old-state new-state]} (get-in @state tx-ref)]
      (if-not diff-computed?
        (let [[add rem] (data/diff new-state old-state)
              env' (assoc env :ref tx-ref)]
          (h/create-entity! env' data-viewer/DataViewer add :set :ui/diff-add-view)
          (h/create-entity! env' data-viewer/DataViewer rem :set :ui/diff-rem-view)
          (swap! state update-in tx-ref assoc :ui/diff-computed? true)))
      (swap! state update-in ref assoc ::active-tx tx-ref))))

(defmutation clear-transactions [_]
  (action [env]
    (let [{:keys [state ref]} env
          tx-refs (get-in @state (conj ref ::tx-list))]
      (swap! state update-in ref assoc ::tx-list [] ::active-tx nil)
      (if (seq tx-refs)
        (swap! state #(reduce h/deep-remove-ref % tx-refs))))))

(om/defui ^:once TransactionList
  static fulcro/InitialAppState
  (initial-state [_ _]
    {::tx-list-id (random-uuid)
     ::tx-list    []
     ::tx-filter  ""})

  static om/Ident
  (ident [_ props] [::tx-list-id (::tx-list-id props)])

  static om/IQuery
  (query [_] [::tx-list-id ::tx-filter
              {::active-tx (om/get-query Transaction)}
              {::tx-list (om/get-query TransactionRow)}])

  static css/CSS
  (local-rules [_] [[:.container {:display        "flex"
                                  :width          "100%"
                                  :flex           "1"
                                  :flex-direction "column"}]
                    [:.tools {:border-bottom "1px solid #dadada"
                              :display       "flex"
                              :align-items   "center"}]

                    [:.tool-separator {:background "#ccc"
                                       :width      "1px"
                                       :height     "16px"
                                       :margin     "0 6px"}]

                    [:.icon ui/css-icon
                     [:&:hover {:text-shadow (str "0 0 0 " ui/color-icon-strong)}]]

                    [:.transactions {:flex     "1"
                                     :overflow "auto"}]

                    [:.input {:color       ui/color-text-normal
                              :outline     "0"
                              :margin      "0 2px"
                              :font-family ui/label-font-family
                              :font-size   ui/label-font-size
                              :padding     "2px 4px"}]

                    [:.active-tx ui/css-dock-details-container]
                    [:.active-container ui/css-dock-details-item-container]
                    [:.active-tools ui/css-dock-details-tools]
                    [:.icon-close ui/css-icon-close]])
  (include-children [_] [Transaction TransactionRow])

  Object
  (render [this]
    (let [{::keys [tx-list active-tx tx-filter]} (om/props this)
          css     (css/get-classnames TransactionList)
          tx-list (if (seq tx-filter)
                    (filter #(str/includes? (-> % :tx pr-str) tx-filter) tx-list)
                    tx-list)]
      (dom/div #js {:className (:container css)}
        (dom/div #js {:className (:tools css)}
          (dom/div #js {:className (:icon css)
                        :title     "Clear transactions"
                        :style     #js {:fontSize "15px"}
                        :onClick   #(om/transact! this [`(clear-transactions {})])}
            "🚫")
          (dom/div #js {:className (css :tool-separator)})
          (dom/input #js {:className   (:input css)
                          :type        "text"
                          :placeholder "Filter"
                          :value       tx-filter
                          :onChange    #(mutations/set-string! this ::tx-filter :event %)}))
        (dom/div #js {:className (:transactions css)}
          (if (seq tx-list)
            (->> tx-list
                 rseq
                 (mapv #(transaction-row %
                          {::on-select
                           (fn [tx]
                             (om/transact! this [`(select-tx ~tx)]))

                           ::selected?
                           (= (::tx-id active-tx) (::tx-id %))})))))
        (if active-tx
          (dom/div #js {:className (:active-tx css)}
            (dom/div #js {:className (:active-tools css)}
              (dom/div #js {:style #js {:flex 1}})
              (dom/div #js {:className (str (css :icon) " " (css :icon-close))
                            :title     "Close panel"
                            :onClick   #(mutations/set-value! this ::active-tx nil)}
                "❌"))
            (dom/div #js {:className (:active-container css)}
              (transaction active-tx))))))))

(def transaction-list (om/factory TransactionList))
