(ns fulcro.inspect.ui.multi-inspector
  (:require
    [cljs.reader :refer [read-string]]
    [fulcro.client.mutations :as mutations]
    [fulcro.inspect.ui.core :as ui]
    [fulcro.inspect.ui.inspector :as inspector]
    [fulcro.client.localized-dom :as dom]
    [fulcro.client.primitives :as fp]
    [fulcro.inspect.helpers :as h]
    [fulcro.inspect.ui.events :as events]
    [fulcro.inspect.helpers :as db.h]
    [garden.core :as g]
    [fulcro-css.css :as css]))

(mutations/defmutation add-inspector [inspector]
  (action [env]
    (let [{:keys [ref state]} env
          inspector-ref (fp/ident inspector/Inspector inspector)
          current       (get-in @state (conj ref ::current-app))]
      (swap! state h/merge-entity inspector/Inspector inspector :append (conj ref ::inspectors))
      (if (nil? current)
        (swap! state update-in ref assoc ::current-app inspector-ref)))))

(mutations/defmutation set-app [{::inspector/keys [id]}]
  (action [env]
    (let [{:keys [ref state]} env]
      (swap! state update-in ref assoc ::current-app [::inspector/id id]))))

(fp/defsc MultiInspector [this {::keys [inspectors current-app] :as props}]
  {:initial-state (fn [_] {::inspectors  []
                           ::current-app nil})

   :ident         (fn [] [::multi-inspector "main"])
   :query         [{::inspectors [::inspector/id ::inspector/name]}
                   {::current-app (fp/get-query inspector/Inspector)}]
   :css           [[:.container {:display        "flex"
                                 :flex-direction "column"
                                 :width          "100%"
                                 :height         "100%"
                                 :overflow       "hidden"}]
                   [:.selector {:font-family ui/label-font-family
                                :font-size   ui/label-font-size
                                :display     "flex"
                                :align-items "center"
                                :background  "#f3f3f3"
                                :color       ui/color-text-normal
                                :border-top  "1px solid #ccc"
                                :padding     "12px"
                                :user-select "none"}]
                   [:.label {:margin-right "10px"}]
                   [:.no-app {:display         "flex"
                              :background      "#f3f3f3"
                              :font-family     ui/label-font-family
                              :font-size       "23px"
                              :flex            1
                              :align-items     "center"
                              :justify-content "center"}]
                   [:body {:margin 0 :padding 0}]]
   :css-include   [inspector/Inspector]}

  (dom/div :.container
    (css/style-element this)
    (if current-app
      (inspector/inspector current-app)
      (dom/div :.no-app
        (dom/div "No app connected.")))
    (if (> (count inspectors) 1)
      (dom/div :.selector
        (dom/div :.label "App")
        (dom/select {:value    (pr-str (::inspector/id current-app))
                     :onChange #(fp/transact! this `[(set-app {::inspector/id ~(read-string (.. % -target -value))})])}
          (for [{::inspector/keys [id name]} (sort-by (comp str ::inspector/name) inspectors)]
            (dom/option {:key   id
                         :value (pr-str id)}
              (str name))))))))

(def multi-inspector (fp/factory MultiInspector {:keyfn ::multi-inspector}))
