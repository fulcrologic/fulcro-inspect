(ns fulcro.inspect.ui.multi-oge
  (:require [com.fulcrologic.fulcro.components :as fp]
            [com.wsscode.oge.core :as oge]
            [fulcro.inspect.helpers :as db.h]
            [com.fulcrologic.fulcro.mutations :as fm]))

(defn select-remote [this remote]
  (fm/set-value! this ::active
    [:oge/id [:fulcro.inspect.core/app-uuid (db.h/comp-app-uuid this) remote]]))

(fm/defmutation set-active-query [{:keys [query]}]
  (action [{:keys [state ref]}]
    (let [active-ref (get-in @state (conj ref ::active))]
      (swap! state update-in active-ref assoc :oge/query query))))

(fp/defsc OgeView
  [this {::keys [oges active]}]
  {:initial-state (fn [{:keys [id remotes]}]
                    (let [oges (mapv #(-> (fp/get-initial-state oge/Oge %)
                                          (assoc :oge/id [:fulcro.inspect.core/app-uuid id %]))
                                 remotes)]
                      {::id     [:x id]
                       ::active (first oges)
                       ::oges   oges}))
   :ident         ::id
   :query         [::id
                   {::active (fp/get-query oge/Oge)}
                   {::oges (fp/get-query oge/Oge)}]
   :css           []
   :css-include   [oge/Oge]}
  (oge/oge active {:fulcro.inspect.client/remotes (mapv :oge/remote oges)
                   :fulcro.inspect.core/app-uuid  (db.h/comp-app-uuid this)
                   ::oge/on-switch-remote         (partial select-remote this)}))

(def oge-view (fp/factory OgeView {:keyfn ::id}))
