(ns fulcro.inspect.ui.dom-history-viewer
  (:require [fulcro.client.primitives :as prim :refer [defsc]]
            [fulcro.client.mutations :as m :refer [defmutation]]
            [fulcro-css.css :as css]
            [fulcro.client.dom :as dom]
            [fulcro.client.primitives :as fp]))

(defmutation show-dom-preview [_]
  (action [{:keys [state]}]
    (swap! state assoc-in [::dom-viewer :singleton :ui/visible?] true))
  (refresh [env] [:ui/historical-dom-view]))

(defmutation hide-dom-preview [_]
  (action [{:keys [state]}]
    (swap! state assoc-in [::dom-viewer :singleton :ui/visible?] false))
  (refresh [env] [:ui/historical-dom-view]))

(defsc DOMHistoryView [this {:keys [ui/visible? app-state]} {:keys [target-app]} {:keys [dom-overlay hidden]}]
  {:query         [:ui/visible? :app-state]
   :initial-state {:ui/visible? false :app-state {}}
   :ident         (fn [] [::dom-viewer :singleton])
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
  (when-let [reconciler (:reconciler target-app)]
    (let [app-root-class         (prim/react-type (prim/app-root reconciler))
          app-root-class-factory (prim/factory app-root-class)
          root-query             (prim/get-query app-root-class app-state)
          view-tree              (prim/db->tree root-query app-state app-state)]
      (dom/div #js {:className (str dom-overlay " " (when-not visible? hidden))
                    :title     "Click to close preview."
                    :onClick   #(fp/transact! this `[(hide-dom-preview {})])}
        (app-root-class-factory view-tree)))))

(def ui-dom-history-view (prim/factory DOMHistoryView))
