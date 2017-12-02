(ns fulcro.inspect.card-helpers
  (:require [fulcro-css.css :as css]
            [fulcro.client.dom :as dom]
            [fulcro.client.primitives :as fp]))

(defn make-root [Root app-id]
  (fp/ui
    static om/InitialAppState
    (initial-state [_ params] {:fulcro.inspect.core/app-id app-id
                               :ui/react-key (random-uuid)
                               :ui/root      (fp/get-initial-state Root params)})

    static fp/IQuery
    (query [_] [:ui/react-key
                {:ui/root (fp/get-query Root)}])

    static css/CSS
    (local-rules [_] [])
    (include-children [_] [Root])

    Object
    (render [this]
      (let [{:ui/keys [react-key root]} (fp/props this)
            factory (fp/factory Root)]
        (dom/div #js {:key react-key}
          (factory root))))))

(defn init-state-atom [comp data]
  (atom (fp/tree->db comp (fp/get-initial-state comp data) true)))
