(ns fulcro.inspect.ui.inspector
  (:require [com.fulcrologic.fulcro-css.localized-dom :as dom]
            [com.fulcrologic.fulcro.mutations :as mutations]
            [com.fulcrologic.fulcro.components :as comp]
            [fulcro.inspect.ui.core :as ui]
            [fulcro.inspect.ui.data-history :as data-history]
            [fulcro.inspect.ui.data-viewer :as data-viewer]
            [fulcro.inspect.ui.db-explorer :as db-explorer]
            [fulcro.inspect.ui.data-watcher :as data-watcher]
            [fulcro.inspect.ui.element :as element]
            [fulcro.inspect.ui.i18n :as i18n]
            [fulcro.inspect.ui.multi-oge :as oge]
            [fulcro.inspect.ui.network :as network]
            [fulcro.inspect.ui.settings :as settings]
            [fulcro.inspect.ui.statecharts :refer [Statecharts ui-statecharts]]
            [fulcro.inspect.ui.transactions :as transactions]))

(comp/defsc Inspector
  [this
   {::keys   [app-state tab client-connection-id
              db-explorer #_element network transactions
              #_i18n oge statecharts settings]
    :ui/keys [more-open?]} _ css]
  {:initial-state
   (fn [{:keys [id] :as params}]
     {::id                   [:x id]
      ::client-connection-id -1
      ::name                 ""
      ::tab                  ::page-db
      ::app-state            (-> (comp/get-initial-state data-history/DataHistory params)
                               (assoc-in [:data-history/watcher ::data-watcher/root-data ::data-viewer/expanded]
                                 {[] true}))
      ;::element              (comp/get-initial-state element/Panel nil)
      ;::i18n                 (comp/get-initial-state i18n/TranslationsViewer nil)
      ::db-explorer          (comp/get-initial-state db-explorer/DBExplorer params)
      ::network              (comp/get-initial-state network/NetworkHistory params)
      ::oge                  (comp/get-initial-state oge/OgeView params)
      ::transactions         (comp/get-initial-state transactions/TransactionList params)
      ;::statecharts          (comp/get-initial-state Statecharts [])
      ::settings             (comp/get-initial-state settings/Settings {})
      :ui/more-open?         false})

   :ident ::id

   :query
   [::tab ::id ::name :fulcro.inspect.core/app-id :ui/more-open? ::client-connection-id
    {[:fulcro.inspect.core/floating-panel "main"] [:ui/dock-side]}
    {::app-state (comp/get-query data-history/DataHistory)}
    {::db-explorer (comp/get-query db-explorer/DBExplorer)}
    ;{::element (comp/get-query element/Panel)}
    {::network (comp/get-query network/NetworkHistory)}
    ;{::i18n (comp/get-query i18n/TranslationsViewer)}
    {::transactions (comp/get-query transactions/TransactionList)}
    {::oge (comp/get-query oge/OgeView)}
    {::statecharts (comp/get-query Statecharts)}
    {::settings (comp/get-query settings/Settings)}]

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
             :position      "relative"
             :user-select   "none"}]
    [:.flex {:flex "1"}]
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
    #_element/Panel #_i18n/TranslationsViewer oge/OgeView db-explorer/DBExplorer]}

  (let [tab-item (fn [{:keys [title html-title disabled? page]}]
                   (dom/div {:className (cond-> (:tab css)
                                          disabled? (str " " (:tab-disabled css))
                                          (= tab page) (str " " (:tab-selected css)))
                             :title     html-title
                             :onClick   #(if-not disabled?
                                           (mutations/set-value! this ::tab page))}
                     title))]
    (dom/div :.container {:onClick #(if more-open? (mutations/set-value! this :ui/more-open? false))}
      (dom/div :.tabs
        (tab-item {:title "DB" :page ::page-db})
        (tab-item {:title "DB Explorer" :page ::page-db-explorer})
        ;(tab-item {:title "Element" :page ::page-element})
        (tab-item {:title "Transactions" :page ::page-transactions})
        (tab-item {:title "Network" :page ::page-network})
        (tab-item {:title "EQL" :page ::page-oge})
        ;(tab-item {:title "i18n" :page ::page-i18n})
        (tab-item {:title "Statecharts" :page ::page-statecharts})
        (tab-item {:title "Settings" :page ::page-settings})
        (dom/div :.flex)
        #_(dom/div #js {:className (:more css)
                        :onClick   (fn [e]
                                     (.stopPropagation e)
                                     (mutations/toggle! this :ui/more-open?))}
            (ui/icon :more_vert)))

      (if more-open?
        (dom/div :.more-panel {:onClick #(.stopPropagation %)}))

      (dom/div :.tab-content
        (case tab
          ::page-db
          (data-history/data-history app-state)

          ::page-db-explorer
          (db-explorer/ui-db-explorer db-explorer)

          #_#_::page-element (element/panel element)

          ::page-transactions
          (transactions/transaction-list transactions)

          ::page-network
          (network/network-history network)

          ::page-oge
          (oge/oge-view oge)

          #_#_::page-i18n (i18n/translations-viewer i18n)
          ::page-statecharts (ui-statecharts statecharts)

          ::page-settings
          (settings/ui-settings settings)

          (dom/div
            "Invalid page " (pr-str tab)))))))

(def inspector (comp/factory Inspector))
