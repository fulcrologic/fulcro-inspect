(ns com.wsscode.oge.ui.network
  (:require [cljsjs.d3]
            [com.fulcrologic.fulcro.components :as fp]
            [com.fulcrologic.fulcro.dom :as dom]))

(fp/defui ^:once RelationGraph
  Object
  (componentDidMount [_]
    )

  (render [this]
    (let [{:keys []} (fp/props this)]
      (dom/div nil
        ))))

(def relation-graph (fp/factory RelationGraph))
