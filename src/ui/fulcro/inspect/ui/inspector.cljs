(ns fulcro.inspect.ui.inspector
  (:require [fulcro-css.css :as css]
            [fulcro.client.mutations :as mutations]
            [fulcro.inspect.ui.core :as ui :refer [colors]]
            [fulcro.inspect.ui.data-history :as data-history]
            [fulcro.inspect.ui.data-viewer :as data-viewer]
            [fulcro.inspect.ui.data-watcher :as data-watcher]
            [fulcro.inspect.ui.element :as element]
            [fulcro.inspect.ui.network :as network]
            [fulcro.inspect.ui.transactions :as transactions]
            [fulcro.inspect.ui.i18n :as i18n]
            [fulcro.inspect.ui.multi-oge :as oge]
            [fulcro.client.dom :as dom]
            [fulcro.client.primitives :as fp]))

(fp/defsc Inspector [this {::keys   [app-state tab element network transactions i18n oge]
                           :ui/keys [more-open?]} _ css]
  {:initial-state
   (fn [state]
     {::id           (random-uuid)
      ::name         ""
      ::tab          ::page-db
      ::app-state    (-> (fp/get-initial-state data-history/DataHistory state)
                         (assoc-in [::data-history/watcher ::data-watcher/root-data ::data-viewer/expanded]
                           {[] true}))
      ::element      (fp/get-initial-state element/Panel nil)
      ::network      (fp/get-initial-state network/NetworkHistory nil)
      ::i18n         (fp/get-initial-state i18n/TranslationsViewer nil)
      ::transactions (fp/get-initial-state transactions/TransactionList [])
      ::oge          (fp/get-initial-state oge/OgeView {})
      :ui/more-open? false})

   :ident
   [::id ::id]

   :query
   [::tab ::id ::name :fulcro.inspect.core/app-id :ui/more-open?
    {[:fulcro.inspect.core/floating-panel "main"] [:ui/dock-side]}
    {::app-state (fp/get-query data-history/DataHistory)}
    {::element (fp/get-query element/Panel)}
    {::network (fp/get-query network/NetworkHistory)}
    {::i18n (fp/get-query i18n/TranslationsViewer)}
    {::transactions (fp/get-query transactions/TransactionList)}
    {::oge (fp/get-query oge/OgeView)}]

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
             :color         (:text-normal colors)
             :border-bottom "1px solid #ccc"
             :position      "relative"
             :user-select   "none"}]
    [:.flex {:flex "1"}]
    [:.tab {:cursor  "pointer"
            :padding "6px 10px 5px"}

     [:&:hover {:background "#e5e5e5"
                :color      (:text-strong colors)}]
     [:&.tab-selected {:border-bottom "2px solid #5c7ebb"
                       :color         (:text-strong colors)
                       :margin-bottom "-1px"}]
     [:&.tab-disabled {:color  (:text-faded colors)
                       :cursor "default"}
      [:&:hover {:background "transparent"}]]]
    [:.tab-content {:flex     "1"
                    :overflow "auto"
                    :display  "flex"}
     [:&.spaced {:padding "10px"}]]

    [:.more {:cursor        "pointer"
             :transform     "scale(0.8)"
             :padding-right "3px"}]
    [:.more-panel {:position      "absolute"
                   :z-index       "1"
                   :right         "0"
                   :top           "26px"
                   :background    "#f3f3f3"
                   :padding       "14px"
                   :border        "1px solid #C8C8C8"
                   :border-radius "4px"
                   :box-shadow    ui/box-shadow}]
    [:.dock-side {:display     "flex"
                  :align-items "center"}]
    [:.dock-title {:margin-right "10px"
                   :font-family  ui/label-font-family
                   :font-size    ui/label-font-size}]
    [:.dock-icon {:cursor "pointer"
                  :width  "14px"
                  :height "12px"
                  :margin "0 5px"}]]

   :css-include
   [data-history/DataHistory network/NetworkHistory transactions/TransactionList
    element/Panel i18n/TranslationsViewer oge/OgeView]}

  (let [tab-item (fn [{:keys [title html-title disabled? page]}]
                   (dom/div #js {:className (cond-> (:tab css)
                                              disabled? (str " " (:tab-disabled css))
                                              (= tab page) (str " " (:tab-selected css)))
                                 :title     html-title
                                 :onClick   #(if-not disabled?
                                               (mutations/set-value! this ::tab page))}
                     title))]
    (dom/div #js {:className (:container css)
                  :onClick   #(if more-open? (mutations/set-value! this :ui/more-open? false))}
      (dom/div #js {:className (:tabs css)}
        (tab-item {:title "DB" :page ::page-db})
        (tab-item {:title "Element" :page ::page-element})
        (tab-item {:title "Transactions" :page ::page-transactions})
        (tab-item {:title "Network" :page ::page-network})
        (tab-item {:title "Query" :page ::page-oge})
        (tab-item {:title "i18n" :page ::page-i18n})
        (dom/div #js {:className (:flex css)})
        #_(dom/div #js {:className (:more css)
                        :onClick   (fn [e]
                                     (.stopPropagation e)
                                     (mutations/toggle! this :ui/more-open?))}
            (ui/icon :more_vert)))

      (if more-open?
        (dom/div #js {:className (:more-panel css)
                      :onClick   #(.stopPropagation %)}))

      (case tab
        ::page-db
        (dom/div #js {:className (:tab-content css)}
          (data-history/data-history app-state))

        ::page-element
        (dom/div #js {:className (:tab-content css)}
          (element/panel element))

        ::page-transactions
        (dom/div #js {:className (:tab-content css)}
          (transactions/transaction-list transactions))

        ::page-network
        (dom/div #js {:className (:tab-content css)}
          (network/network-history network))

        ::page-oge
        (dom/div #js {:className (:tab-content css)}
          (oge/oge-view oge))

        ::page-i18n
        (dom/div #js {:className (:tab-content css)}
          (i18n/translations-viewer i18n))

        (dom/div #js {:className (:tab-content css)}
          "Invalid page " (pr-str tab))))))

(def inspector (fp/factory Inspector))
