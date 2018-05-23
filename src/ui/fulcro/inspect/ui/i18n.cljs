(ns fulcro.inspect.ui.i18n
  (:require [fulcro.client.mutations :as mutations :refer [defmutation]]
            [cljs.reader :refer [read-string]]
            [fulcro-css.css :as css]
            [fulcro.i18n :as i18n]
            [fulcro.inspect.ui.core :as ui]
            [fulcro.inspect.helpers :as h]
            [fulcro.client.localized-dom :as dom]
            [fulcro.client.primitives :as fp]))

(defonce reconciler-by-app-id (atom {}))

(fp/defsc TranslationsViewer [this {::keys [locales current-locale]}]
  {:initial-state (fn [params] (merge {::id (random-uuid)} params))
   :ident [::id ::id]
   :query [::id 
           {::locales (fp/get-query i18n/Locale)}
           {::current-locale (fp/get-query i18n/Locale)}]
   :css [[:.label {:font-family "sans-serif"
                   :font-size "12px"
                   :font-weight 300} #_ui/css-info-label]
         [:.container {:width "100%"
                      :flex 1
                      :display "flex"
                      :flex-direction "column"}]
         [:.locale-switcher {:margin "3px"}]]}
  (dom/div :.container
           (ui/toolbar
            nil
            (dom/div :.locale-switcher
                     (dom/label :.label "Current locale: "
                                (dom/select {:value (-> current-locale ::i18n/locale pr-str)
                                             :onChange #(fp/transact! this `[(change-locale {:locale ~(read-string (.. % -target -value))})])}
                                            (for [{::i18n/keys [locale] :ui/keys [locale-name]} locales]
                                              (dom/option {:key (pr-str locale) :value (pr-str locale)} locale-name))))))))

(def translations-viewer (fp/factory TranslationsViewer))

(defmutation change-locale [{:keys [locale app-id]}]
  (action [{:keys [state ref]}]
          (let [app-id (or app-id (-> ref second second))] 
            (fp/transact! (get @reconciler-by-app-id app-id) `[(i18n/change-locale {:locale ~locale})]))))

(defmutation set-locales [{::keys [current-locale locales] :as props}]
  (action [{:keys [state ref]}]
          (swap! state h/merge-entity TranslationsViewer {::locales locales
                                                          ::id (-> (get-in @state ref) ::id)})
          (swap! state update-in ref assoc ::current-locale current-locale)))
