(ns fulcro.inspect.ui.element-cards
  (:require
    [devcards.core :refer-macros [defcard]]
    [fulcro-css.css :as css]
    [fulcro.inspect.ui.network-cards :as net.cards]
    [fulcro.client.cards :refer-macros [defcard-fulcro]]
    [fulcro.inspect.ui.network :as network]
    [fulcro.inspect.ui.element :as element]
    [om.next :as om]
    [fulcro.client.core :as fulcro]
    [om.dom :as dom]))

(om/defui ^:once ElementRoot
  static fulcro/InitialAppState
  (initial-state [_ _] {:ui/react-key (random-uuid)
                        :ui/network   (assoc (fulcro/get-initial-state network/NetworkHistory {})
                                        ::network/history-id "main")
                        :ui/element   (fulcro/get-initial-state element/Panel {})})

  static om/IQuery
  (query [_] [{:ui/network (om/get-query network/NetworkHistory)}
              {:ui/element (om/get-query element/Panel)}
              :ui/react-key])

  static css/CSS
  (local-rules [_] [[:.container {}]])
  (include-children [_] [network/NetworkHistory element/Panel])

  Object
  (render [this]
    (let [{:ui/keys [react-key network element]} (om/props this)
          css (css/get-classnames ElementRoot)]
      (dom/div #js {:key react-key :className (:container css)}
        (dom/button #js {:onClick #(net.cards/gen-request this)}
          "Generate request")
        (network/network-history network)
        (element/panel element)))))

(defcard-fulcro element
  ElementRoot
  {})

(css/upsert-css "element" ElementRoot)
