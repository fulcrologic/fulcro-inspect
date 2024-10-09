(ns fulcro.inspect.ui.i18n
  (:require [com.fulcrologic.fulcro.mutations :as mutations :refer [defmutation]]
            [cljs.reader :refer [read-string]]
            [com.fulcrologic.fulcro-css.css :as css]
            [com.fulcrologic.fulcro-i18n.i18n :as i18n]
            [fulcro.inspect.ui.core :as ui]
            [fulcro.inspect.helpers :as h]
            [com.fulcrologic.fulcro-css.localized-dom :as dom]
            [com.fulcrologic.fulcro.components :as fp]
            [fulcro.inspect.helpers :as db.h]))

(declare TranslationsViewer)

(defmutation change-locale [{:keys [locale]}]
  (action [env]
    (db.h/swap-entity! env assoc ::current-locale locale))
  (remote [env]
    (-> (db.h/remote-mutation env 'transact)
        (update :params assoc :tx `[(i18n/change-locale {:locale ~locale})]))))

(fp/defsc TranslationsViewer [this {::keys [locales current-locale]}]
  {:initial-state (fn [params] (merge {::id (random-uuid)} params))
   :ident         [::id ::id]
   :query         [::id
                   ::locales
                   ::current-locale]
   :css           [[:.label {:font-family ui/label-font-family
                             :font-size   ui/label-font-size
                             :margin      "0 4px"}]
                   [:.container {:flex           1
                                 :display        "flex"
                                 :flex-direction "column"}]
                   [:.locale-switcher {:margin "3px"}]]}
  (dom/div :.container
    (ui/toolbar {}
      (dom/div :.locale-switcher
        (dom/label :.label "Current locale: ")
        (dom/select {:value    (-> current-locale pr-str)
                     :onChange #(fp/transact! this `[(change-locale {:locale ~(read-string (.. % -target -value))})])}
          (for [[locale locale-name] locales]
            (dom/option {:key (pr-str locale) :value (pr-str locale)} locale-name)))))))

(def translations-viewer (fp/factory TranslationsViewer))
