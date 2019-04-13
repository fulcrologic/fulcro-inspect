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
      {:refresh       [::explorer]
       :marker        [::index-marker id]
       :update-query  (fn [query]
                        (vary-meta query assoc :remote-data {:fulcro.inspect.core/app-uuid app-id
                                                             :fulcro.inspect.client/remote remote}))})))

(fp/defsc IndexExplorer
  [this {::keys [id explorer explorers] :as props} {}]
  {:initial-state (fn [{:keys [app-uuid remotes]}]
                    (let [explorers (mapv
                                      #(-> {::iex/id    [:fulcro.inspect.core/app-uuid app-uuid %]
                                            ::iex/index {}})
                                      remotes)]
                      {::id        [:fulcro.inspect.core/app-uuid app-uuid]
                       ::explorer  (first explorers)
                       ::explorers explorers}))
   :ident         [::id ::id]
   :query         [::id [df/marker-table '_]
                   {::explorer (fp/get-query iex/IndexExplorer)}
                   {::explorers (fp/get-query iex/IndexExplorer)}]
   :css           [[:.container {:display        "flex"
                                 :flex           "1"
                                 :flex-direction "column"
                                 :width          "100%"}
                    [:a {:color           "#6ea6e4"
                         :font-weight     "bold"
                         :text-decoration "none"}]]
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
                             :padding         "30px"
                             :align-items     "center"
                             :justify-content "center"
                             :color           "#fff"
                             :font-size       "21px"}
                    [:&.help {:font-size "14px"}]]
                   [:.help {:flex       "1"
                            :background "#777"
                            :margin-top "-5px"
                            :padding    "30px"
                            :color      "#fff"
                            :font-size  "14px"}]
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
      (cond
        (df/loading? (get-in props [df/marker-table [::index-marker (::iex/id explorer)]]))
        (dom/div :.empty "Loading...")

        (-> explorer ::iex/idx ::iex/no-index?)
        (dom/div :.help
          (dom/div
            "Seems like the index is not available. Find information on how to setup the
            integration at "
            (dom/a {:href "https://wilkerlucio.github.io/pathom/#_setting_up_the_index_explorer_resolver" :target "_blank"}
              " pathom docs") "."))

        (-> explorer ::iex/idx seq)
        (iex/index-explorer explorer)

        :else
        (dom/div :.empty "Use the \"Load index\" button to start.")))))

(def index-explorer (fp/computed-factory IndexExplorer))
