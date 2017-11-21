(ns fulcro.inspect.ui.inspector
  (:require [fulcro-css.css :as css]
            [fulcro.client.core :as fulcro]
            [fulcro.client.mutations :as mutations]
            [fulcro.inspect.ui.core :as ui]
            [fulcro.inspect.ui.data-watcher :as data-watcher]
            [fulcro.inspect.ui.network :as network]
            [fulcro.inspect.ui.transactions :as transactions]
            [om.dom :as dom]
            [om.next :as om]))

(om/defui ^:once Inspector
  static fulcro/InitialAppState
  (initial-state [_ state]
    {::id           (random-uuid)
     ::tab          ::page-db
     ::app-state    (fulcro/get-initial-state data-watcher/DataWatcher state)
     ::network      (fulcro/get-initial-state network/NetworkHistory nil)
     ::transactions (fulcro/get-initial-state transactions/TransactionList [])
     :ui/network?   false})

  static om/IQuery
  (query [_] [::tab ::id :ui/network?
              {::app-state (om/get-query data-watcher/DataWatcher)}
              {::network (om/get-query network/NetworkHistory)}
              {::transactions (om/get-query transactions/TransactionList)}])

  static om/Ident
  (ident [_ props] [::id (::id props)])

  static css/CSS
  (local-rules [_] [[:.container {:display        "flex"
                                  :flex-direction "column"
                                  :width          "100%"
                                  :height         "100%"
                                  :overflow       "hidden"}]
                    [:.tabs {:font-family   ui/label-font-family
                             :font-size     ui/label-font-size
                             :display       "flex"
                             :background    "#f3f3f3"
                             :color         ui/color-text-normal
                             :border-bottom "1px solid #ccc"
                             :user-select   "none"}]
                    [:.tab {:cursor  "pointer"
                            :padding "6px 10px 5px"}
                     [:&:hover {:background "#e5e5e5"
                                :color      ui/color-text-strong}]
                     [:&.tab-selected {:border-bottom "2px solid #5c7ebb"
                                       :color         ui/color-text-strong
                                       :margin-bottom "-1px"}]
                     [:&.tab-disabled {:color  ui/color-text-faded
                                       :cursor "default"}
                      [:&:hover {:background "transparent"}]]]
                    [:.tab-content {:flex     "1"
                                    :overflow "auto"}
                     [:&.spaced {:padding "10px"}]]])
  (include-children [_] [data-watcher/DataWatcher
                         network/NetworkHistory
                         transactions/TransactionList])

  Object
  (render [this]
    (let [{::keys   [app-state tab network transactions]
           :ui/keys [network?]} (om/props this)
          css      (css/get-classnames Inspector)
          tab-item (fn [{:keys [title html-title disabled? page]}]
                     (dom/div #js {:className (cond-> (:tab css)
                                                disabled? (str " " (:tab-disabled css))
                                                (= tab page) (str " " (:tab-selected css)))
                                   :title     html-title
                                   :onClick   #(if-not disabled?
                                                 (mutations/set-value! this ::tab page))}
                       title))]
      (dom/div #js {:className (:container css)}
        (dom/div #js {:className (:tabs css)}
          (tab-item {:title "DB" :page ::page-db})
          (tab-item {:title "Element" :disabled? true})
          (tab-item (cond-> {:title "Network" :page ::page-network}
                      (not network?)
                      (assoc :disabled? true :html-title "You need to wrap your network with network-inspector to enable the network panel.")))
          (tab-item {:title "Transactions" :page ::page-transactions})
          (tab-item {:title "OgE" :disabled? true}))

        (case tab
          ::page-db
          (dom/div #js {:className (str (:tab-content css) " " (:spaced css))}
            (data-watcher/data-watcher app-state))

          ::page-network
          (dom/div #js {:className (:tab-content css)}
            (network/network-history network))

          ::page-transactions
          (dom/div #js {:className (:tab-content css)}
            (transactions/transaction-list transactions))

          (dom/div #js {:className (:tab-content css)}
            "Invalid page " (pr-str tab)))))))

(def inspector (om/factory Inspector))
