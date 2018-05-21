(ns fulcro.inspect.ui.dom-history-viewer
  (:require [fulcro.client.primitives :as prim :refer [defsc]]
            [fulcro.client.mutations :as m :refer [defmutation]]
            [fulcro-css.css :as css]
            [fulcro.client.localized-dom :as dom]
            [fulcro.client.primitives :as fp]
            [fulcro.inspect.helpers :as db.h]))

(defmutation show-dom-preview [{:keys [state]}]
  (action [env]
    (db.h/swap-entity! env assoc :ui/visible? true :app-state state))

  (refresh [env] [:ui/historical-dom-view]))

(defmutation hide-dom-preview [_]
  (action [env]
    (db.h/swap-entity! env assoc :ui/visible? false))
  (refresh [env] [:ui/historical-dom-view]))

(defsc DOMHistoryView [this {:keys [ui/visible? app-state]} _ css]
  {:initial-state {:ui/visible? false :app-state {}}
   :ident         (fn [] [::dom-viewer :singleton])
   :query         [:ui/visible? :app-state]
   :css           [[:.close {:margin-left "100px"}]
                   [:.hidden {:display :none}]
                   [:.dom-overlay {:z-index          10000
                                   :position         :absolute
                                   :width            "100vw"
                                   :height           "100vh"
                                   :top              0
                                   :left             0
                                   :opacity          0.8
                                   :background-color :white}]]}

  (dom/div :.dom-overlay {:className (when-not visible? (:hidden css))
                          :title     "Click to close preview."
                          :onClick   #(fp/transact! this `[(hide-dom-preview {})])}
    (when-let [render-fn (:render-fn (meta app-state))]
      (render-fn app-state))))

(def ui-dom-history-view (prim/factory DOMHistoryView))
