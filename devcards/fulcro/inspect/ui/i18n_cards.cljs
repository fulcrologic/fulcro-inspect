(ns fulcro.inspect.ui.i18n-cards
  (:require
   [devcards.core :refer-macros [defcard]]
   [fulcro-css.css :as css]
   [fulcro.client.cards :refer-macros [defcard-fulcro]]
   [fulcro.client.primitives :as fp :refer [defsc]]
   [fulcro.client.data-fetch :as fetch]
   [fulcro.client.mutations :as mutations]
   [clojure.test.check.generators :as gen]
   [fulcro.inspect.card-helpers :as card-helpers]
   [fulcro.inspect.ui.i18n :as inspect-i18n]
   [fulcro.i18n :as fulcro-i18n]
   [fulcro.client.dom :as dom]
   [cljs.spec.alpha :as s]))

(def TranslationsViewerRoot (card-helpers/make-root inspect-i18n/TranslationsViewer ::test))

(defcard-fulcro locale-picker TranslationsViewerRoot 
  (card-helpers/init-state-atom TranslationsViewerRoot {::inspect-i18n/current-locale (fp/get-initial-state fulcro.i18n/Locale {:locale :en})
                                                        ::inspect-i18n/locales [(fp/get-initial-state fulcro.i18n/Locale {:locale :en})
                                                                                (fp/get-initial-state fulcro.i18n/Locale {:locale :pt})
                                                                                (fp/get-initial-state fulcro.i18n/Locale {:locale :nl})]}))
