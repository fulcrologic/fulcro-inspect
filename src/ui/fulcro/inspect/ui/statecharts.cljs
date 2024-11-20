(ns fulcro.inspect.ui.statecharts
  (:require
    [clojure.edn :as edn]
    [com.fulcrologic.fulcro-css.localized-dom :as dom]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.algorithms.normalized-state :as fns]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom.events :as evt]
    [com.fulcrologic.fulcro.mutations :as m]
    [com.fulcrologic.fulcro.mutations :refer [defmutation]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.statecharts.visualization.visualizer :as viz]
    [fulcro.inspect.helpers :as db.h]
    [fulcro.inspect.ui.core :as ui]
    [taoensso.timbre :as log]))

(declare Statecharts)

(defsc StatechartDefinition [this props]
  {:ident :statechart/registry-key
   :query [:statechart/registry-key
           :statechart/chart]})

(defsc StatechartSession [this props]
  {:ident :com.fulcrologic.statecharts/session-id
   :query [:com.fulcrologic.statecharts/session-id
           :com.fulcrologic.statecharts/history-value
           :com.fulcrologic.statecharts/parent-session-id
           :com.fulcrologic.statecharts/statechart-src
           :com.fulcrologic.statecharts/configuration
           {:com.fulcrologic.statecharts/statechart (comp/get-query StatechartDefinition)}]})

(defn get-available-sessions [this]
  (let [app-id (db.h/comp-app-uuid this)]
    (merge/merge-component! this viz/Visualizer {} :replace (conj (comp/get-ident this) :ui/viz))
    (df/load this :statecharts Statecharts
      {:params {:fulcro.inspect.core/app-uuid app-id
                :target                       (comp/get-ident this)}})))

(defsc Statecharts [this {:statechart/keys [available-sessions
                                            definitions]
                          :ui/keys         [current-session viz] :as props}]
  {:ident             ::id
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
                     :onChange (fn [evt]
                                 (when-let [v (some-> (evt/target-value evt) (edn/read-string))]
                                   (m/set-value! this :ui/current-session [:com.fulcrologic.statecharts/session-id v])))}
          (mapv
            (fn [{:com.fulcrologic.statecharts/keys [statechart-src session-id]}]
              (dom/option {:value (pr-str session-id)
                           :key   (pr-str session-id)}
                (str statechart-src "@" session-id)))
            available-sessions)))
      (dom/div
        (dom/pre
          (str (:com.fulcrologic.statecharts/configuration current-session))))
      (when (and viz current-session)
        (viz/ui-visualizer viz {:chart (some-> current-session :com.fulcrologic.statecharts/statechart :statechart/chart)
                                :current-configuration (:com.fulcrologic.statecharts/configuration current-session)})))))

(def ui-statecharts (comp/factory Statecharts))
