(ns fulcro.inspect.ui.oge
  (:require [fulcro.client.primitives :as fp]
            [fulcro.client.localized-dom :as dom]
            [com.wsscode.oge.core :as oge]))

(fp/defsc OgeView
  [this {::keys [oge]}]
  {:initial-state (fn [_] {::id  (random-uuid)
                           ::oge (fp/get-initial-state oge/Oge {})})
   :ident         [::id ::id]
   :query         [::id
                   {::oge (fp/get-query oge/Oge)}]
   :css           [[:$flex {:flex "1"}]]
   :css-include   [oge/Oge]}
  (oge/oge oge))

(def oge-view (fp/factory OgeView {:keyfn ::id}))
