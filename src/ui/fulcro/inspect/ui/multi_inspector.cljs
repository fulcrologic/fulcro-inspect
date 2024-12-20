(ns fulcro.inspect.ui.multi-inspector
  (:require
    [cljs.reader :refer [read-string]]
    [com.fulcrologic.fulcro-css.css-injection :refer [style-element]]
    [com.fulcrologic.fulcro-css.localized-dom :as dom]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.algorithms.normalized-state :as fns]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as fp]
    [com.fulcrologic.fulcro.dom.events :as evt]
    [com.fulcrologic.fulcro.mutations :as m]
    [com.fulcrologic.fulcro.raw.components :as rc]
    [fulcro.inspect.helpers :as db.h]
    [fulcro.inspect.ui.core :as ui]
    [fulcro.inspect.ui.inspector :as inspector]
    [fulcro.inspect.ui.settings :as settings]))

(m/defmutation add-inspector [inspector]
  (action [env]
    (let [{:keys [ref state]} env
          inspector-ref (fp/ident inspector/Inspector inspector)
          current       (get-in @state (conj ref ::current-app))]
      (swap! state merge/merge-component inspector/Inspector inspector :append (conj ref ::inspectors))
      (if (nil? current)
        (swap! state update-in ref assoc ::current-app inspector-ref)))))

(m/defmutation remove-inspector [{::app/keys [id]}]
  (action [{:keys [state] :as env}]
    (let [inspector-ref [::inspector/id [:x id]]
          ref           [::multi-inspector "main"]]
      (swap! state fns/remove-entity inspector-ref #{::inspector/app-state
                                                     ::inspector/db-explorer
                                                     :history/id
                                                     ::inspector/network})

      #_(when-not (get-in @state (conj ref ::current-app))
        (swap! state assoc-in (conj ref ::current-app)
          (first (get-in @state (conj ref ::inspectors))))))))

(m/defmutation set-app [{::inspector/keys [id]}]
  (action [env]
    (let [{:keys [ref state]} env]
      (swap! state update-in ref assoc ::current-app [::inspector/id id]))))

(m/defmutation toggle-settings [_]
  (action [env]
    (db.h/swap-entity! env update ::show-settings? not)))

(rc/defnc InspectorRef [::inspector/id ::inspector/name])

(fp/defsc MultiInspector [this {::keys [inspectors current-app show-settings? settings]}]
  {:initial-state (fn [_] {::inspectors     []
                           ::settings       (fp/get-initial-state settings/Settings {})
                           ::current-app    nil
                           ::show-settings? false
                           })
   :ident         (fn [] [::multi-inspector "main"])
   :query         [::show-settings?
                   {::settings (fp/get-query settings/Settings)}
                   {::inspectors (fp/get-query InspectorRef)}
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
                   [:.flex {:flex "1"}]
                   [:.stale-notice
                    {:background  "#fff4c5"
                     :display     "flex"
                     :align-items "center"
                     :padding     "7px 10px"}]
                   [:body {:margin 0 :padding 0}]]
   :css-include   [inspector/Inspector settings/Settings]}
  (dom/div :.container
    (style-element {:component this})
    (let [toggle-settings! #(fp/transact! this [(toggle-settings {})])]
      (if show-settings?
        (settings/ui-settings settings {:close-settings! toggle-settings!})
        (if current-app
          (inspector/inspector current-app)
          (dom/div :.no-app
            (dom/div "No app connected.")
            (dom/div :$margin-left-standard
              (ui/button {:onClick toggle-settings!}
                "Show Settings"))))))
    (if (> (count inspectors) 1)
      (dom/div :.selector
        (dom/div :.label "App")
        (dom/select {:value    (pr-str (::inspector/id current-app))
                     :onChange #(fp/transact! this [(set-app {::inspector/id (read-string (evt/target-value %))})])}
          (for [{::inspector/keys [id name]} (sort-by (comp str ::inspector/name) inspectors)]
            (when id
              (dom/option {:key   id
                           :value (pr-str id)}
                (str name)))))))))

(def multi-inspector (fp/factory MultiInspector {:keyfn ::multi-inspector}))
