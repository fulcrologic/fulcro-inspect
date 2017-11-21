(ns fulcro.inspect.card-helpers
  (:require [fulcro.client.core :as fulcro]
            [fulcro-css.css :as css]
            [om.dom :as dom]
            [om.next :as om]))

(defn make-root [Root app-id]
  (om/ui
    static fulcro/InitialAppState
    (initial-state [_ params] {:fulcro.inspect.core/app-id app-id
                               :ui/react-key (random-uuid)
                               :ui/root      (fulcro/get-initial-state Root params)})

    static om/IQuery
    (query [_] [:ui/react-key
                {:ui/root (om/get-query Root)}])

    static css/CSS
    (local-rules [_] [])
    (include-children [_] [Root])

    Object
    (render [this]
      (let [{:ui/keys [react-key root]} (om/props this)
            factory (om/factory Root)]
        (dom/div #js {:key react-key}
          (factory root))))))

(defn init-state-atom [comp data]
  (atom (om/tree->db comp (fulcro/get-initial-state comp data) true)))
