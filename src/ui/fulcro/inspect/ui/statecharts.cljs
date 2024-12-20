(ns fulcro.inspect.ui.statecharts
  (:require
    [clojure.edn :as edn]
    [com.fulcrologic.fulcro-css.localized-dom :as dom]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.dom.events :as evt]
    [com.fulcrologic.fulcro.mutations :as m]
    [com.fulcrologic.fulcro.mutations :refer [defmutation]]
    ;[com.fulcrologic.statecharts.visualization.visualizer :as viz]
    [com.fulcrologic.statecharts :as sc]
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
    #_(merge/merge-component! this viz/Visualizer {} :replace (conj (comp/get-ident this) :ui/viz))
    (df/load this :statecharts Statecharts
      {:params {:fulcro.inspect.core/app-uuid app-id
                :target                       (comp/get-ident this)}})))

(defsc Statecharts [this {:statechart/keys [available-sessions
                                            definitions]
                          :ui/keys         [current-session] :as props}]
  {:ident             ::id
   :query             [::id
                       #_{:ui/viz (comp/get-query viz/Visualizer)}
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
                     :onChange (fn [evt]
                                 (when-let [v (some-> (evt/target-value evt) (edn/read-string))]
                                   (m/set-value! this :ui/current-session [:com.fulcrologic.statecharts/session-id v])))}
          (mapv
            (fn [{:com.fulcrologic.statecharts/keys [statechart-src session-id]}]
              (dom/option {:value (pr-str session-id)
                           :key   (pr-str session-id)}
                (str statechart-src "@" session-id)))
            available-sessions)))
      #_(when (and viz current-session)
          (viz/ui-visualizer viz {:chart                 (some-> current-session :com.fulcrologic.statecharts/statechart :statechart/chart)
                                  :current-configuration (:com.fulcrologic.statecharts/configuration current-session)})))))

(def ui-statecharts (comp/factory Statecharts))
