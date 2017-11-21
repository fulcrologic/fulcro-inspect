(ns fulcro.inspect.ui.inspector
  (:require [fulcro-css.css :as css]
            [fulcro.client.core :as fulcro]
            [fulcro.client.mutations :as mutations]
            [fulcro.inspect.ui.core :as ft.ui]
            [fulcro.inspect.ui.data-watcher :as f.data-watcher]
            [fulcro.inspect.ui.network :as f.ui.network]
            [om.dom :as dom]
            [om.next :as om]))

(om/defui ^:once Inspector
  static fulcro/InitialAppState
  (initial-state [_ state]
    {::id         (random-uuid)
     ::tab        ::page-db
     ::app-state  (fulcro/get-initial-state f.data-watcher/DataWatcher state)
     ::network    (fulcro/get-initial-state f.ui.network/NetworkHistory nil)
     :ui/network? false})

  static om/IQuery
  (query [_] [::tab ::id :ui/network?
              {::app-state (om/get-query f.data-watcher/DataWatcher)}
              {::network (om/get-query f.ui.network/NetworkHistory)}])

  static om/Ident
  (ident [_ props] [::id (::id props)])

  static css/CSS
  (local-rules [_] [[:.container {:display        "flex"
                                  :flex-direction "column"
                                  :width          "100%"
                                  :height         "100%"
                                  :overflow       "hidden"}]
                    [:.tabs (merge ft.ui/label-font
                                   {:display       "flex"
                                    :background    "#f3f3f3"
                                    :color         "#5a5a5a"
                                    :border-bottom "1px solid #ccc"
                                    :user-select   "none"})]
                    [:.tab {:cursor  "pointer"
                            :padding "6px 10px 5px"}
                     [:&:hover {:background "#e5e5e5"
                                :color      "#333"}]
                     [:&.tab-selected {:border-bottom "2px solid #5c7ebb"
                                       :color         "#333"
                                       :margin-bottom "-1px"}]
                     [:&.tab-disabled {:color  "#bbb"
                                       :cursor "default"}
                      [:&:hover {:background "transparent"}]]]
                    [:.tab-content {:padding  "10px"
                                    :flex     "1"
                                    :overflow "auto"}]])
  (include-children [_] [f.data-watcher/DataWatcher
                         f.ui.network/NetworkHistory])

  Object
  (render [this]
    (let [{::keys   [app-state tab network]
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
          (tab-item {:title "Transactions" :disabled? true})
          (tab-item {:title "OgE" :disabled? true}))

        (dom/div #js {:className (:tab-content css)}
          (case tab
            ::page-db
            (f.data-watcher/data-watcher app-state)

            ::page-network
            (f.ui.network/network-history network)

            (dom/div nil
              "Invalid page " (pr-str tab))))))))

(def inspector (om/factory Inspector))
