(ns fulcro.inspect.card-helpers
  (:require [fulcro-css.css :as css]
            [fulcro.client.dom :as dom]
            [fulcro.i18n :as i18n]
            [fulcro.client.primitives :as fp]))

(defn make-root [Root app-id]
  (fp/ui
    static fp/InitialAppState
    (initial-state [_ params] {:fulcro.inspect.core/app-id app-id
                               ::i18n/current-locale (fp/get-initial-state i18n/Locale {:locale :en :name "English"})
                               ;; TODO Actually load from po files
                               ::i18n/locale-by-id [(fp/get-initial-state i18n/Locale {:locale :en 
                                                                                       :name "English" 
                                                                                       :translations {["" "Add item"] "Add item"}})
                                                    (fp/get-initial-state i18n/Locale {:locale :pt 
                                                                                       :name "Portuguese" 
                                                                                       :translations {["" "Add item"] "adicionar Item "}})
                                                    (fp/get-initial-state i18n/Locale {:locale :nl 
                                                                                       :name "Dutch" 
                                                                                       :translations {["" "Add item"] "Voeg item toe"}})]
                               :ui/react-key (random-uuid)
                               :ui/root      (fp/get-initial-state Root params)})

    static fp/IQuery
    (query [_] [:ui/react-key
                {::i18n/current-locale (fp/get-query i18n/Locale)}
                {::i18n/locale-by-id (fp/get-query i18n/Locale)}
                {:ui/root (fp/get-query Root)}])

    static css/CSS
    (local-rules [_] [])
    (include-children [_] [Root])

    Object
    (render [this]
      (let [{:ui/keys [react-key root]} (fp/props this)
            factory (fp/factory Root)]
        (dom/div #js {:key react-key}
                 (factory root)
                 (css/style-element Root))))))

(defn init-state-atom [comp data]
  (atom (fp/tree->db comp (fp/get-initial-state comp data) true)))
