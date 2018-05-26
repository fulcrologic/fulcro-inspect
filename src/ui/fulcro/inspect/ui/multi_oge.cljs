(ns fulcro.inspect.ui.multi-oge
  (:require [fulcro.client.primitives :as fp]
            [fulcro.client.localized-dom :as dom]
            [com.wsscode.oge.core :as oge]
            [fulcro.inspect.helpers :as db.h]))

(fp/defsc OgeView
  [this {::keys [oges active]}]
  {:initial-state (fn [{:keys [app-uuid remotes]}]
                    (let [oges (mapv #(-> (fp/get-initial-state oge/Oge %)
                                          (assoc :oge/id [:fulcro.inspect.core/app-uuid app-uuid %]))
                                 remotes)]
                      {::id     [:fulcro.inspect.core/app-uuid app-uuid]
                       ::active (first oges)
                       ::oges   oges}))
   :ident         [::id ::id]
   :query         [::id
                   {::active (fp/get-query oge/Oge)}
                   {::oges (fp/get-query oge/Oge)}]
   :css           []
   :css-include   [oge/Oge]}
  (oge/oge active {:fulcro.inspect.client/remotes (mapv :oge/remote oges)
                   :fulcro.inspect.core/app-uuid  (db.h/comp-app-uuid this)}))

(def oge-view (fp/factory OgeView {:keyfn ::id}))
