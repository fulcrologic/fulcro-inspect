(ns fulcro.inspect.ui.element
  (:require
    [fulcro-css.css :as css]
    [fulcro-css.css-protocols :as cssp]
    [fulcro.client.localized-dom :as dom]
    [fulcro.client.mutations :as mutations]
    [fulcro.client.primitives :as fp :refer [get-query]]
    [fulcro.inspect.helpers :as h]
    [fulcro.inspect.ui.core :as ui]
    [fulcro.inspect.ui.data-viewer :as data-viewer]))

(fp/defui ^:once Details
  static fp/InitialAppState
  (initial-state [_ {::keys [props static-query query] :as params}]
    (merge
      {::detail-id         (random-uuid)
       ::props-view        (fp/get-initial-state data-viewer/DataViewer props)
       ::static-query-view (fp/get-initial-state data-viewer/DataViewer static-query)
       ::query-view        (fp/get-initial-state data-viewer/DataViewer query)}
      params))

  static fp/Ident
  (ident [_ props] [::detail-id (::detail-id props)])

  static fp/IQuery
  (query [_] [::detail-id ::display-name ::ident
              {::props-view (fp/get-query data-viewer/DataViewer)}
              {::static-query-view (fp/get-query data-viewer/DataViewer)}
              {::query-view (fp/get-query data-viewer/DataViewer)}])

  static cssp/CSS
  (local-rules [_] [[:.container {:flex     "1"
                                  :overflow "auto"
                                  :padding  "0 10px"}]])
  (include-children [_] [])

  Object
  (render [this]
    (let [{::keys [display-name ident props-view static-query-view query-view]} (fp/props this)
          css (css/get-classnames Details)]
      (dom/div #js {:className (:container css)}
        (ui/info {::ui/title "Display Name"}
          (ui/comp-display-name {} display-name))
        (ui/info {::ui/title "Ident"}
          (ui/ident {} ident))
        (ui/info {::ui/title "Props"}
          (data-viewer/data-viewer props-view {:raw? true}))
        (ui/info {::ui/title "Static (startup) Query"}
          (data-viewer/data-viewer static-query-view {:raw? true}))
        (ui/info {::ui/title "Current (dynamic) Query"}
          (data-viewer/data-viewer query-view {:raw? true}))))))

(def details (fp/factory Details))

(mutations/defmutation set-element [details]
  (action [env]
    (h/swap-entity! env assoc :ui/picking? false)
    (h/swap-in! env [::details] assoc ::ident nil)
    (h/remove-edge! env ::details)
    (h/create-entity! env Details details :set ::details)))

(mutations/defmutation remote-pick-element [_]
  (remote [env]
    (h/remote-mutation env 'pick-element)))

(fp/defui ^:once Panel
  static fp/InitialAppState
  (initial-state [this _]
    {::panel-id (random-uuid)})

  static fp/Ident
  (ident [_ props] [::panel-id (::panel-id props)])

  static fp/IQuery
  (query [_] [::panel-id :ui/picking?
              {::details (fp/get-query Details)}])

  static cssp/CSS
  (local-rules [_] [[:.container {:flex           1
                                  :display        "flex"
                                  :flex-direction "column"}
                     [:.icon-active
                      [:svg.c-icon {:fill "#4682E9"}]]]])
  (include-children [_] [ui/CSS Details])

  Object
  (render [this]
    (let [{:keys [ui/picking? ::panel-id] :as props} (fp/props this)
          css (css/get-classnames Panel)]
      (dom/div #js {:className (:container css)}
        (ui/toolbar {}
          (ui/toolbar-action (cond-> {:onClick #(do
                                                  (mutations/set-value! this :ui/picking? true)
                                                  (fp/transact! this [`(remote-pick-element {})]))}
                               picking? (assoc :className (:icon-active css)))
            (ui/icon :gps_fixed) "Pick Element"))
        (if (::details props)
          (details (::details props))
          (dom/div {:style {:margin "20pt"}}
            (dom/h2 "Click on the Pick Element button, then click in your application's UI to inspect.")
            (dom/h4 "Note")
            (dom/p
              "The element picker requires a UI plugin for the target client application (which could
               be native, web, etc.).  If you are inspecting a web client, then you can simply add:")
            (dom/p {:style {:fontFamily "Courier"}}
              (dom/pre {}
                ":devtools {:preloads [;  your websocket or normal preload here, then:\n"
                "                      com.fulcrologic.fulcro.inspect.dom-picker-preload]}"))
            (dom/p "in your shadow-cljs.edn build to install the necessary support.")))))))

(def panel (fp/factory Panel))
