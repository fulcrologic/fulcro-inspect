(ns fulcro.inspect.ui.statecharts
  (:require
    [clojure.edn :as edn]
    [com.fulcrologic.devtools.common.resolvers :as dres]
    [com.fulcrologic.devtools.devtool-io :as dio]
    [com.fulcrologic.fulcro-css.localized-dom :as dom]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom.events :as evt]
    [com.fulcrologic.fulcro.mutations :as m]
    [com.fulcrologic.fulcro.mutations :refer [defmutation]]
    [com.fulcrologic.statecharts :as sc]
    [com.fulcrologic.statecharts.integration.fulcro-impl :refer [statechart-event]]
    [com.fulcrologic.statecharts.visualization.visualizer :as viz]
    [com.wsscode.pathom.connect :as pc]
    [fulcro.inspect.helpers :as db.h]
    [fulcro.inspect.ui.core :as ui]))

(declare Statecharts)

(defsc StatechartDefinition [this props]
  {:ident :statechart/registry-key
   :query [:statechart/registry-key
           :statechart/chart]})

(defsc StatechartSession [this props]
  {:ident ::sc/session-id
   :query [::sc/session-id
           ::sc/history-value
           ::sc/parent-session-id
           ::sc/statechart-src
           ::sc/configuration
           {::sc/statechart (comp/get-query StatechartDefinition)}]})

(defmutation update-session [{::sc/keys [session-id
                                         configuration]}]
  (action [{:keys [state]}]
    (swap! state assoc-in [::sc/session-id session-id ::sc/configuration] configuration)))

(defn get-available-sessions [this]
  (let [app-id (db.h/comp-app-uuid this)]
    (merge/merge-component! this viz/Visualizer {} :replace (conj (comp/get-ident this) :ui/viz))
    (dio/load! this app-id (comp/get-ident this) Statecharts
      {:params {:com.fulcrologic.fulcro.application/id app-id}})))

(dres/defmutation statechart-event-mutation [{:fulcro/keys [app]} params]
  {::pc/sym `statechart-event}
  (comp/transact! app [(update-session params)])
  nil)

(defsc Statecharts [this {:statechart/keys [available-sessions
                                            definitions]
                          :ui/keys         [current-session viz] :as props}]
  {:ident             ::id
   :initial-state     (fn [{:keys [id]}]
                        {::id                [:x id]
                         :ui/current-session nil
                         :ui/viz             (comp/get-initial-state viz/Visualizer)})
   :query             [::id
                       {:ui/viz (comp/get-query viz/Visualizer)}
                       {:ui/current-session (comp/get-query StatechartSession)}
                       {:statechart/available-sessions (comp/get-query StatechartSession)}
                       {:statechart/definitions (comp/get-query StatechartDefinition)}]
   :componentDidMount get-available-sessions
   :css               [[:.container {:padding "12px"}]]}
  (dom/div :.container
    (ui/header {} "Statecharts")
    (dom/div :$margin-left-standard
      (ui/row {:classes [:.align-center]}
        (ui/label {} "Statechart Session:")
        (dom/select {:name     "session-id"
                     :id       "session-id"
                     :value    (pr-str (:com.fulcrologic.statecharts/session-id current-session))
                     :onClick  (fn [evt]
                                 (let [app-id (db.h/comp-app-uuid this)]
                                   (dio/load! this app-id :statechart/available-sessions StatechartSession
                                     {:params {:com.fulcrologic.fulcro.application/id app-id}
                                      :target (conj (comp/get-ident this) :statechart/available-sessions)})))
                     :onChange (fn [evt]
                                 (when-let [v (some-> (evt/target-value evt) (edn/read-string))]
                                   (m/set-value! this :ui/current-session [:com.fulcrologic.statecharts/session-id v])))}
          (mapv
            (fn [{:com.fulcrologic.statecharts/keys [statechart-src session-id]}]
              (dom/option {:value (pr-str session-id)
                           :key   (pr-str session-id)}
                (str statechart-src "@" session-id)))
            available-sessions)))
      (when (and viz current-session)
        (viz/ui-visualizer viz {:chart                 (some-> current-session :com.fulcrologic.statecharts/statechart :statechart/chart)
                                :current-configuration (:com.fulcrologic.statecharts/configuration current-session)})))))

(def ui-statecharts (comp/factory Statecharts))
