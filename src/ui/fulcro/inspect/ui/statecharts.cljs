(ns fulcro.inspect.ui.statecharts
  (:require
    [clojure.edn :as edn]
    [com.fulcrologic.devtools.common.resolvers :as dres]
    [com.fulcrologic.devtools.devtool-io :as dio]
    [com.fulcrologic.fulcro-css.localized-dom :as dom]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom.events :as evt]
    [com.fulcrologic.fulcro.mutations :as m]
    [com.fulcrologic.fulcro.mutations :refer [defmutation]]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
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

(defmutation set-zoom [{:keys [zoom-level]}]
  (action [{:keys [state ref]}]
    (swap! state assoc-in (conj ref :ui/zoom-level) zoom-level)))

(dres/defmutation statechart-event-mutation [{:fulcro/keys [app]} params]
  {::pc/sym `statechart-event}
  (comp/transact! app [(update-session params)])
  nil)

(defsc Statecharts [this {:statechart/keys [available-sessions]
                          :ui/keys         [current-session viz]}]
  {:ident         ::id
   :initial-state (fn [{:keys [id]}]
                    {::id                [:x id]
                     :ui/current-session nil
                     :ui/viz             (comp/get-initial-state viz/Visualizer {})})
   :query         [::id
                   {:ui/viz (comp/get-query viz/Visualizer)}
                   {:ui/current-session (comp/get-query StatechartSession)}
                   {:statechart/available-sessions (comp/get-query StatechartSession)}
                   {:statechart/definitions (comp/get-query StatechartDefinition)}]
   :use-hooks?    true
   :css           [[:.container {:display        "flex"
                                 :flex-direction "column"
                                 :height         "100%"
                                 :padding        "12px"
                                 :overflow       "hidden"}]
                   [:.fixed-header {:flex             "0 0 auto"
                                    :background-color "#fff"
                                    :z-index          "10"
                                    :width            "100%"
                                    :position         "relative"}]
                   [:.zoom-controls {:display          "flex"
                                     :align-items      "center"
                                     :gap              "8px"
                                     :margin           "12px 0"
                                     :padding          "8px"
                                     :background-color "#f5f5f5"
                                     :border-radius    "4px"}]
                   [:.zoom-button {:padding          "4px 12px"
                                   :border           "1px solid #ccc"
                                   :border-radius    "3px"
                                   :background-color "#fff"
                                   :cursor           "pointer"
                                   :font-size        "14px"}]
                   [:.zoom-button:hover {:background-color "#e0e0e0"}]
                   [:.zoom-label {:font-size "14px"
                                  :color     "#666"}]
                   [:.scrollable-content {:flex       "1 1 auto"
                                          :overflow   "auto"
                                          :min-height "0"
                                          :width      "100%"}]
                   [:.visualizer-wrapper {:border           "1px solid #ddd"
                                          :border-radius    "4px"
                                          :background-color "#fff"
                                          :display          "inline-block"
                                          :min-width        "100%"}]
                   [:.visualizer-scaled {:transform-origin "top left"}]]}
  (let [[zoom-level set-zoom-level!] (hooks/use-state 1.0)]
    (dom/div :.container
      (dom/div :.fixed-header
        (ui/header {} "Statecharts")
        (dom/div :$margin-left-standard
          (ui/row {:classes [:.align-center]}
            (ui/label {} "Statechart Session:")
            (dom/select {:name     "session-id"
                         :id       "session-id"
                         :value    (or (some-> current-session :com.fulcrologic.statecharts/session-id pr-str) "")
                         :onClick  (fn [evt]
                                     (let [app-id (db.h/comp-app-uuid this)]
                                       (dio/load! this app-id :statechart/available-sessions StatechartSession
                                         {:params {:com.fulcrologic.fulcro.application/id app-id}
                                          :target (conj (comp/get-ident this) :statechart/available-sessions)})))
                         :onChange (fn [evt]
                                     (let [v (evt/target-value evt)]
                                       (if (= v "")
                                         (m/set-value! this :ui/current-session nil)
                                         (let [parsed      (edn/read-string v)
                                               state-map   (app/current-state this)
                                               ident       (when parsed [:com.fulcrologic.statecharts/session-id parsed])
                                               new-session (when parsed (get-in state-map ident))
                                               chart-id    (some-> new-session :com.fulcrologic.statecharts/statechart-src)]
                                           (when chart-id
                                             (set-zoom-level! 1.0)
                                             (m/set-value! this :ui/current-session [:com.fulcrologic.statecharts/session-id parsed]))))))}
              (concat
                [(dom/option {:value "" :key "placeholder"} "-- Select a statechart session --")]
                (mapv
                  (fn [{:com.fulcrologic.statecharts/keys [statechart-src session-id]}]
                    (dom/option {:value (pr-str session-id)
                                 :key   (pr-str session-id)}
                      (str statechart-src "@" session-id)))
                  available-sessions))))
          (when (and viz current-session)
            (dom/div :.zoom-controls
              (dom/button :.zoom-button
                {:onClick #(set-zoom-level! (max 0.1 (- (or zoom-level 0.7) 0.1)))}
                "Zoom Out (−)")
              (dom/button :.zoom-button
                {:onClick #(set-zoom-level! (min 2.0 (+ (or zoom-level 0.7) 0.1)))}
                "Zoom In (+)")
              (dom/button :.zoom-button
                {:onClick #(set-zoom-level! 1.0)}
                "Reset (100%)")
              (dom/span :.zoom-label
                (str "Zoom: " (int (* (or zoom-level 0.7) 100)) "%"))))))
      (when (and viz current-session)
        (dom/div :.scrollable-content
          (dom/div :.visualizer-wrapper
            (dom/div :.visualizer-scaled
              {:style {:transform (str "scale(" (or zoom-level 0.7) ")")}}
              (viz/ui-visualizer viz {:chart                 (some-> current-session :com.fulcrologic.statecharts/statechart :statechart/chart)
                                      :current-configuration (:com.fulcrologic.statecharts/configuration current-session)}))))))))

(def ui-statecharts (comp/factory Statecharts))
