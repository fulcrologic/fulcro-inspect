(ns fulcro.inspect.ui.inspector
  (:require [fulcro-css.css :as css]
            [fulcro.client.mutations :as mutations]
            [fulcro.inspect.ui.core :as ui]
            [fulcro.inspect.ui.data-history :as data-history]
            [fulcro.inspect.ui.data-viewer :as data-viewer]
            [fulcro.inspect.ui.data-watcher :as data-watcher]
            [fulcro.inspect.ui.element :as element]
            [fulcro.inspect.ui.network :as network]
            [fulcro.inspect.ui.transactions :as transactions]
            [fulcro.client.dom :as dom]
            [fulcro.client.primitives :as fp]))

(fp/defsc Inspector [this {::keys [target-app app-state tab element network transactions]} _ css]
  {:initial-state
   (fn [state] {::id           (random-uuid)
                ::tab          ::page-db
                ::app-state    (-> (fp/get-initial-state data-history/DataHistory state)
                                   (assoc-in [::data-history/watcher ::data-watcher/root-data ::data-viewer/expanded]
                                     {[] true}))
                ::element      (fp/get-initial-state element/Panel nil)
                ::network      (fp/get-initial-state network/NetworkHistory nil)
                ::transactions (fp/get-initial-state transactions/TransactionList [])})

   :ident
   [::id ::id]

   :query
   [::tab ::id
    ::target-app
    {::app-state (fp/get-query data-history/DataHistory)}
    {::element (fp/get-query element/Panel)}
    {::network (fp/get-query network/NetworkHistory)}
    {::transactions (fp/get-query transactions/TransactionList)}]

   :css
   [[:.container {:display        "flex"
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
                    :overflow "auto"
                    :display  "flex"}
     [:&.spaced {:padding "10px"}]]]

   :css-include
   [data-history/DataHistory network/NetworkHistory transactions/TransactionList element/Panel]}

  (let [tab-item (fn [{:keys [title html-title disabled? page]}]
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
        (tab-item {:title "Element" :page ::page-element})
        (tab-item {:title "Transactions" :page ::page-transactions})
        (tab-item {:title "Network" :page ::page-network})
        (tab-item {:title "OgE" :disabled? true}))

      (case tab
        ::page-db
        (dom/div #js {:className (:tab-content css)}
          (data-history/data-history (fp/computed app-state {:target-app target-app})))

        ::page-element
        (dom/div #js {:className (:tab-content css)}
          (element/panel element))

        ::page-transactions
        (dom/div #js {:className (:tab-content css)}
          (transactions/transaction-list transactions))

        ::page-network
        (dom/div #js {:className (:tab-content css)}
          (network/network-history network))

        (dom/div #js {:className (:tab-content css)}
          "Invalid page " (pr-str tab))))))

(def inspector (fp/factory Inspector))
