(ns fulcro.inspect.ui.multi-inspector
  (:require
    [cljs.reader :refer [read-string]]
    [fulcro-css.css :as css]
    [fulcro.client.mutations :as mutations]
    [fulcro.inspect.ui.core :as ui]
    [fulcro.inspect.ui.inspector :as inspector]
    [fulcro.client.dom :as dom]
    [fulcro.client.primitives :as fp]
    [fulcro.inspect.helpers :as h]))

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

(fp/defsc MultiInspector [this {::keys [inspectors current-app]} _ css]
  {:initial-state (fn [_] {::inspectors  []
                           ::current-app nil})

   :ident         (fn [] [::multi-inspector "main"])
   :query         [::inspectors {::current-app (fp/get-query inspector/Inspector)}]
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
                              :justify-content "center"}]]
   :css-include   [inspector/Inspector]}

  (dom/div #js {:className (:container css)}
    (if current-app
      (inspector/inspector current-app)
      (dom/div #js {:className (:no-app css)}
        (dom/div nil "No app connected.")))
    (if (> (count inspectors) 1)
      (dom/div #js {:className (:selector css)}
        (dom/div #js {:className (:label css)} "App")
        (dom/select #js {:value    (str (::inspector/id current-app))
                         :onChange #(fp/transact! this `[(set-app {::inspector/id ~(read-string (.. % -target -value))})])}
          (for [app-id (->> (map (comp pr-str second) inspectors) sort)]
            (dom/option #js {:key   app-id
                             :value app-id}
              app-id)))))))

(def multi-inspector (fp/factory MultiInspector {:keyfn ::multi-inspector}))
