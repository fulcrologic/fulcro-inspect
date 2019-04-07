(ns fulcro.inspect.ui.index-explorer
  (:require [com.wsscode.pathom.viz.index-explorer :as iex]
            [cljs.reader :refer [read-string]]
            [fulcro.client.primitives :as fp]
            [fulcro.client.localized-dom :as dom]
            [fulcro.client.data-fetch :as df]
            [fulcro.inspect.helpers :as db.h]
            [fulcro.client.mutations :as fm]
            [fulcro.inspect.ui.core :as cui]))

(defn explorer->remote [{::iex/keys [id]}]
  (if (vector? id) (peek id) id))

(defn load-index [this id]
  (let [app-id (try (db.h/comp-app-uuid this) (catch :default _))
        remote (explorer->remote {::iex/id id})]
    (df/load this [::iex/id id] iex/IndexExplorer
      {:update-query (fn [query]
                       (into (with-meta [] (meta query))
                             (map (fn [x]
                                    (if (= x ::iex/index)
                                      (list ::iex/index {:fulcro.inspect.core/app-uuid app-id
                                                         :fulcro.inspect.client/remote remote})
                                      x)))
                             query))})))

(fp/defsc IndexExplorer
  [this {::keys [id explorer explorers] :as props} {}]
  {:initial-state (fn [{:keys [app-uuid remotes]}]
                    (let [explorers (mapv
                                      #(-> {::iex/id [:fulcro.inspect.core/app-uuid app-uuid %]})
                                      remotes)]
                      {::id        [:fulcro.inspect.core/app-uuid app-uuid]
                       ::explorer  (first explorers)
                       ::explorers explorers}))
   :ident         [::id ::id]
   :query         [::id
                   {::explorer (fp/get-query iex/IndexExplorer)}
                   {::explorers (fp/get-query iex/IndexExplorer)}]
   :css           [[:.container {:display        "flex"
                                 :flex           "1"
                                 :flex-direction "column"
                                 :width          "100%"}]
                   [:.title {:grid-area     "title"
                             :display       "flex"
                             :align-items   "center"
                             :padding       "3px"
                             :margin-bottom "5px"
                             :border-bottom "1px solid #e0e0e0"}]
                   [:.flex {:flex "1"}]
                   [:.empty {:flex            "1"
                             :background      "#777"
                             :margin-top      "-5px"
                             :display         "flex"
                             :align-items     "center"
                             :justify-content "center"
                             :color           "#fff"
                             :font-size       "21px"}]
                   [:.remote-selector {:display     "flex"
                                       :align-items "center"
                                       :font-family cui/label-font-family
                                       :font-size   cui/label-font-size
                                       :color       cui/color-text-normal}]
                   [:.remote-selector-label {:margin-right "8px"
                                             :margin-left  "4px"}]]}
  (let [remotes  (mapv explorer->remote explorers)
        remote   (explorer->remote explorer)
        app-uuid (peek id)]
    (js/console.log "REMOTES" explorer remotes remote)
    (dom/div :.container
      (dom/div :.title
        (if (> (count remotes) 1)
          (dom/div :.remote-selector
            (dom/div :.remote-selector-label "Remote:")
            (dom/select {:onChange #(fm/set-value! this ::explorer
                                      [::iex/id
                                       [:fulcro.inspect.core/app-uuid app-uuid
                                        (read-string (fm/target-value %))]])
                         :value    (pr-str remote)}
              (for [r remotes]
                (dom/option {:key (pr-str r) :value (pr-str r)} (pr-str r))))))
        (dom/div :.flex)
        (dom/button {:onClick #(load-index this [:fulcro.inspect.core/app-uuid app-uuid remote])}
          "Load index"))
      (if explorer
        (if (::iex/index explorer)
          (iex/index-explorer explorer)
          (dom/div :.empty "Seems like the index is not available."))
        (dom/div :.empty "No data")))))

(def index-explorer (fp/computed-factory IndexExplorer))
