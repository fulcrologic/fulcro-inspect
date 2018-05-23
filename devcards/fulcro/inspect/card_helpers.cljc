(ns fulcro.inspect.card-helpers
  (:require #?(:clj [fulcro.client.dom-server :as dom]
               :cljs [fulcro.client.dom :as dom])
            [fulcro-css.css :as css]
            [fulcro.i18n :as i18n]
            [fulcro.client.primitives :as fp])
  #?(:cljs (:require-macros fulcro.inspect.card-helpers)))

#?(:clj (defmacro load-locale [key name]
          (let [{::i18n/keys [locale translations]} (i18n/load-locale "i18n" key)]
            {:locale locale
             :translations translations
             :name name})))

#?(:cljs
   (defn make-root [Root app-id]
     (fp/ui
      static fp/InitialAppState
      (initial-state [_ params] {:fulcro.inspect.core/app-id app-id
                                 ::i18n/current-locale       (fp/get-initial-state i18n/Locale (fulcro.inspect.card-helpers/load-locale :en "English"))
                                 ::i18n/locale-by-id         [(fp/get-initial-state i18n/Locale (fulcro.inspect.card-helpers/load-locale :en "English"))
                                                              (fp/get-initial-state i18n/Locale (fulcro.inspect.card-helpers/load-locale :pt "Portugues"))
                                                              (fp/get-initial-state i18n/Locale (fulcro.inspect.card-helpers/load-locale :nl "Dutch"))]
                                 :ui/root                    (fp/get-initial-state Root params)})

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
                    factory                     (fp/factory Root)]
                (dom/div #js {:key react-key}
                         (factory root)
                         (css/style-element Root)))))))
#(:cljs
  (defn init-state-atom [comp data]
    (atom (fp/tree->db comp (fp/get-initial-state comp data) true))))
