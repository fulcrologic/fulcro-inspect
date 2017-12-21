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

(defsc DOMHistoryView [this {:keys [ui/visible? app-state]} {:keys [target-app]} {:keys [dom_overlay close heading]}]
  {:query             [:ui/visible? :app-state]
   :initial-state     {:ui/visible? false :app-state {}}
   :ident             (fn [] [::dom-viewer :singleton])
   :componentDidMount (fn [] (css/upsert-css "dom_history_view" DOMHistoryView))
   :css               [[:.close {:margin-left "100px"}]
                       [:.heading {:color            :black
                                   :background-color :lightgray
                                   :z-index          60000
                                   :padding          "10px"
                                   :position         :fixed
                                   :top              0
                                   :left             0
                                   :margin-top       0
                                   :margin-left      0}]
                       [:$hidden {:display :none}]
                       [:.dom_overlay {:z-index          10000
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
      (dom/div #js {:className (str dom_overlay (when-not visible? " hidden"))}
        (dom/h4 #js {:className heading} "App Preview"
          (dom/button #js {:className close
                           :onClick   #(fp/transact! this `[(hide-dom-preview {})])} "Close Preview"))
        (app-root-class-factory view-tree)))))

(def ui-dom-history-view (prim/factory DOMHistoryView))
