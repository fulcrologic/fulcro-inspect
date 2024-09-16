(ns fulcro.inspect.ui.element-cards
  (:require
    [devcards.core :refer-macros [defcard]]
    [com.fulcrologic.fulcro-css.css :as css]
    [fulcro.inspect.ui.network-cards :as net.cards]
    [fulcro.client.cards :refer-macros [defcard-fulcro]]
    [fulcro.inspect.ui.network :as network]
    [fulcro.inspect.ui.element :as element]
    [com.fulcrologic.fulcro.components :as fp]
    [com.fulcrologic.fulcro.dom :as dom]))

(fp/defui ^:once ElementRoot
  static fp/InitialAppState
  (initial-state [_ _] {:ui/react-key (random-uuid)
                        :ui/network   (assoc (fp/get-initial-state network/NetworkHistory {})
                                        ::network/history-id "main")
                        :ui/element   (-> (fp/get-initial-state element/Panel {})
                                          (assoc ::element/panel-id ["panel" `ElementRoot]))})

  static fp/IQuery
  (query [_] [{:ui/network (fp/get-query network/NetworkHistory)}
              {:ui/element (fp/get-query element/Panel)}
              :ui/react-key])

  static css/CSS
  (local-rules [_] [[:.container {}]])
  (include-children [_] [network/NetworkHistory element/Panel])

  Object
  (render [this]
    (let [{:ui/keys [react-key network element]} (fp/props this)
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
