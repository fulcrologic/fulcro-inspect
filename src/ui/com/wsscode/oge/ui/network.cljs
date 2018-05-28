(ns com.wsscode.oge.ui.network
  (:require [cljsjs.d3]
            [fulcro.client.dom :as dom]
            [fulcro.client.primitives :as fp]))

(fp/defui ^:once RelationGraph
  Object
  (componentDidMount [_]
    )

  (render [this]
    (let [{:keys []} (fp/props this)]
      (dom/div nil
        ))))

(def relation-graph (fp/factory RelationGraph))
