(ns fulcro.inspect.core
  (:require [cljs.reader :refer [read-string]]
            [goog.functions :as gfun]
            [goog.object :as gobj]
            [fulcro.client.core :as fulcro]
            [fulcro.client.mutations :as mutations :refer-macros [defmutation]]
            [fulcro.client.network :as f.network]
            [fulcro.inspect.helpers :as h]
            [fulcro.inspect.ui.core :as ft.ui]
            [fulcro.inspect.ui.data-viewer :as f.data-viewer]
            [fulcro.inspect.ui.data-watcher :as f.data-watcher]
            [fulcro.inspect.ui.events :as f.events]
            [fulcro.inspect.ui.network :as f.ui.network]
            [fulcro-css.css :as css]
            [om.dom :as dom]
            [om.next :as om]))

(defn set-style! [node prop value]
  (gobj/set (gobj/get node "style") prop value))

(om/defui ^:once Inspector
  static fulcro/InitialAppState
  (initial-state [_ state]
    {::inspector-id   (random-uuid)
     ::inspector-page ::page-db
     ::app-state      (fulcro/get-initial-state f.data-watcher/PinnableDataViewer state)
     ::network        (fulcro/get-initial-state f.ui.network/NetworkHistory nil)
     :ui/network?     false})

  static om/IQuery
  (query [_] [::inspector-page ::inspector-id :ui/network?
              {::app-state (om/get-query f.data-watcher/PinnableDataViewer)}
              {::network (om/get-query f.ui.network/NetworkHistory)}])

  static om/Ident
  (ident [_ props] [::inspector-id (::inspector-id props)])

  static css/CSS
  (local-rules [_] [[:.container {:display        "flex"
                                  :flex-direction "column"
                                  :width          "100%"
                                  :height         "100%"
                                  :overflow       "hidden"}]
                    [:.tabs {:display       "flex"
                             :background    "#f3f3f3"
                             :color         "#5a5a5a"
                             :border-bottom "1px solid #ccc"
                             :font-family   "sans-serif"
                             :font-size     "12px"
                             :user-select   "none"}]
                    [:.tab {:cursor  "pointer"
                            :padding "6px 10px 5px"}
                     [:&:hover {:background "#e5e5e5"
                                :color      "#333"}]
                     [:&.tab-selected {:border-bottom "2px solid #5c7ebb"
                                       :color         "#333"
                                       :margin-bottom "-1px"}]
                     [:&.tab-disabled {:color  "#bbb"
                                       :cursor "default"}
                      [:&:hover {:background "transparent"}]]]
                    [:.tab-content {:padding  "10px"
                                    :flex     "1"
                                    :overflow "auto"}]])
  (include-children [_] [f.data-watcher/PinnableDataViewer
                         f.ui.network/NetworkHistory])

  Object
  (render [this]
    (let [{::keys   [app-state inspector-page network]
           :ui/keys [network?]} (om/props this)
          css      (css/get-classnames Inspector)
          tab-item (fn [{:keys [title html-title disabled? page]}]
                     (dom/div #js {:className (cond-> (:tab css)
                                                disabled? (str " " (:tab-disabled css))
                                                (= inspector-page page) (str " " (:tab-selected css)))
                                   :title     html-title
                                   :onClick   #(if-not disabled?
                                                 (mutations/set-value! this ::inspector-page page))}
                       title))]
      (dom/div #js {:className (:container css)}
        (dom/div #js {:className (:tabs css)}
          (tab-item {:title "DB" :page ::page-db})
          (tab-item {:title "Element" :disabled? true})
          (tab-item (cond-> {:title "Network" :page ::page-network}
                      (not network?)
                      (assoc :disabled? true :html-title "You need to wrap your network with network-inspector to enable the network panel.")))
          (tab-item {:title "Transactions" :disabled? true})
          (tab-item {:title "OgE" :disabled? true}))

        (dom/div #js {:className (:tab-content css)}
          (case inspector-page
            ::page-db
            (f.data-watcher/pinnable-data-viewer app-state)

            ::page-network
            (f.ui.network/network-history network)

            (dom/div nil
              "Invalid page " (pr-str inspector-page))))))))

(def inspector (om/factory Inspector))

;;;;;;;;;;;;;;;;;

(defmutation add-inspector [inspector]
  (action [env]
    (let [{:keys [ref state]} env
          inspector-ref (om/ident Inspector inspector)
          current       (get-in @state (conj ref ::current-app))]
      (swap! state (comp #(h/merge-entity % Inspector inspector)
                         #(update-in % ref update ::inspectors conj inspector-ref)
                         #(cond-> % (nil? current) (update-in ref assoc ::current-app inspector-ref)))))))

(defmutation set-app [{::keys [inspector-id]}]
  (action [env]
    (let [{:keys [ref state]} env]
      (swap! state update-in ref assoc ::current-app [::inspector-id inspector-id]))))

(om/defui ^:once MultiInspector
  static fulcro/InitialAppState
  (initial-state [_ _] {::inspectors  []
                        ::current-app nil})

  static om/IQuery
  (query [_] [::multi-inspector
              ::inspectors
              {::current-app (om/get-query Inspector)}])

  static om/Ident
  (ident [_ props] [::multi-inspector "main"])

  static css/CSS
  (local-rules [_] [[:.container {:display        "flex"
                                  :flex-direction "column"
                                  :width          "100%"
                                  :height         "100%"
                                  :overflow       "hidden"}]
                    [:.selector {:display     "flex"
                                 :align-items "center"
                                 :background  "#f3f3f3"
                                 :color       "#5a5a5a"
                                 :border-top  "1px solid #ccc"
                                 :padding     "12px"
                                 :font-family "sans-serif"
                                 :font-size   "12px"
                                 :user-select "none"}]
                    [:.label {:margin-right "10px"}]
                    [:.no-app (merge
                                ft.ui/label-font
                                {:display         "flex"
                                 :background      "#f3f3f3"
                                 :font-size       "23px"
                                 :flex            1
                                 :align-items     "center"
                                 :justify-content "center"})]])
  (include-children [_] [Inspector])

  Object
  (render [this]
    (let [{::keys [inspectors current-app]} (om/props this)
          css (css/get-classnames MultiInspector)]
      (dom/div #js {:className (:container css)}
        (if current-app
          (inspector current-app)
          (dom/div #js {:className (:no-app css)}
            (dom/div nil "No app connected.")))
        (if (> (count inspectors) 1)
          (dom/div #js {:className (:selector css)}
            (dom/div #js {:className (:label css)} "App")
            (dom/select #js {:value    (str (::inspector-id current-app))
                             :onChange #(om/transact! this `[(set-app {::inspector-id ~(read-string (.. % -target -value))})])}
              (for [app-id (->> (map (comp pr-str second) inspectors) sort)]
                (dom/option #js {:key   app-id
                                 :value app-id}
                  app-id)))))))))

(def multi-inspector (om/factory MultiInspector))

(om/defui ^:once GlobalInspector
  static fulcro/InitialAppState
  (initial-state [_ params] {:ui/size      50
                             :ui/visible?  false
                             :ui/inspector (fulcro/get-initial-state MultiInspector params)})

  static om/IQuery
  (query [_] [{:ui/inspector (om/get-query MultiInspector)}
              :ui/size :ui/visible?])

  static om/Ident
  (ident [_ props] [::floating-panel "main"])

  static css/CSS
  (local-rules [_] [[:.container {:background "#fff"
                                  :box-shadow "rgba(0, 0, 0, 0.3) 0px 0px 4px"
                                  :position   "fixed"
                                  :top        "0"
                                  :right      "0"
                                  :bottom     "0"
                                  :width      "50%"
                                  :overflow   "auto"
                                  :z-index    "9999"}]
                    [:.resizer {:position    "fixed"
                                :cursor      "col-resize"
                                :top         "0"
                                :left        "50%"
                                :margin-left "-5px"
                                :width       "10px"
                                :bottom      "0"
                                :z-index     "99999"}]])
  (include-children [_] [MultiInspector])

  Object
  (componentDidMount [this]
    (gobj/set this "resize-debouncer"
      (gfun/debounce #(mutations/set-value! this :ui/size %) 300)))

  (render [this]
    (let [{:ui/keys [size visible? inspector]} (om/props this)
          keystroke (or (om/shared this [:options :launch-keystroke]) "ctrl-f")
          size      (or size 50)
          css       (css/get-classnames GlobalInspector)]
      (dom/div #js {:className (:reset css)
                    :style     (if visible? nil #js {:display "none"})}
        (f.events/key-listener {::f.events/action    #(mutations/set-value! this :ui/visible? (not visible?))
                                ::f.events/keystroke keystroke})
        (dom/div #js {:className (:resizer css)
                      :ref       #(gobj/set this "resizer" %)
                      :style     #js {:left (str size "%")}
                      :draggable true
                      :onDrag    (fn [e]
                                   (.preventDefault e)
                                   (let [mouse (.-clientX e)
                                         vw    js/document.body.clientWidth
                                         pos   (* (/ mouse vw) 100)]
                                     (when (pos? pos)
                                       (set-style! (gobj/get this "resizer") "left" (str pos "%"))
                                       (set-style! (gobj/get this "container") "width" (str (- 100 pos) "%"))
                                       ((gobj/get this "resize-debouncer") pos))))})
        (dom/div #js {:className (:container css)
                      :style     #js {:width (str (- 100 size) "%")}
                      :ref       #(gobj/set this "container" %)}
          (multi-inspector inspector))))))

(def global-inspector-view (om/factory GlobalInspector))

(om/defui ^:once GlobalRoot
  static fulcro/InitialAppState
  (initial-state [_ _] {:ui/react-key (random-uuid)
                        :ui/root      (fulcro/get-initial-state GlobalInspector {})})

  static om/IQuery
  (query [_] [{:ui/root (om/get-query GlobalInspector)}
              :ui/react-key])

  static css/CSS
  (local-rules [_] [])
  (include-children [_] [GlobalInspector])

  Object
  (render [this]
    (let [{:keys [ui/react-key ui/root]} (om/props this)]
      (dom/div #js {:key react-key}
        (global-inspector-view root)))))

(defonce ^:private global-inspector* (atom nil))

(defn start-global-inspector [options]
  (let [app  (fulcro/new-fulcro-client :shared {:options options})
        node (js/document.createElement "div")]
    (js/document.body.appendChild node)
    (css/upsert-css "fulcro.inspector" GlobalRoot)
    (fulcro/mount app GlobalRoot node)))

(defn global-inspector
  ([] @global-inspector*)
  ([options]
   (or @global-inspector*
       (reset! global-inspector* (start-global-inspector options)))))

(defn update-inspect-state [reconciler app-id state]
  (om/transact! reconciler [::f.data-watcher/id [::app-id app-id]]
    [`(f.data-watcher/update-state ~state) ::f.data-viewer/content]))

(defn inspect-network-init [network app]
  (some-> network :options ::app* (reset! app)))

(defn inspect-app [app-id target-app]
  (let [inspector     (global-inspector)
        state*        (some-> target-app :reconciler :config :state)
        new-inspector (-> (fulcro/get-initial-state Inspector @state*)
                          (assoc ::inspector-id app-id)
                          (assoc-in [::app-state ::f.data-watcher/id] [::app-id app-id])
                          (assoc-in [::network ::f.ui.network/history-id] [::app-id app-id]))]
    (om/transact! (:reconciler inspector) [::multi-inspector "main"]
      [`(add-inspector ~new-inspector)
       ::inspectors])

    (when (inspect-network-init (-> target-app :networking :remote) {:inspector inspector
                                                                     :app       target-app})
      (om/transact! (:reconciler inspector) [::inspector-id app-id]
        `[(mutations/set-props {:ui/network? true})]))

    (add-watch state* app-id
      #(update-inspect-state (:reconciler inspector) app-id %4))

    new-inspector))

;;; network

(defrecord TransformNetwork [network options]
  f.network/NetworkBehavior
  (serialize-requests? [this]
    (f.network/serialize-requests? network))

  f.network/FulcroNetwork
  (send [_ edn ok error]
    (let [{::keys [transform-query transform-response transform-error app*]
           :or    {transform-query    (fn [_ x] x)
                   transform-response (fn [_ x] x)
                   transform-error    (fn [_ x] x)}} options
          req-id (random-uuid)
          env    {::request-id req-id
                  ::app        @app*}]
      (if-let [edn' (transform-query env edn)]
        (f.network/send network edn'
          #(->> % (transform-response env) ok)
          #(->> % (transform-error env) error))
        (ok nil))))

  (start [this]
    (f.network/start network)
    this))

(defn transform-network [network options]
  (->TransformNetwork network (assoc options ::app* (atom nil))))

(defn app-id [reconciler]
  (or (some-> reconciler om/app-state deref ::app-id)
      (some-> reconciler om/app-root om/react-type (gobj/get "displayName") symbol)))

(defn inspect-network
  ([network]
   (transform-network network
     {::transform-query
      (fn [{::keys [request-id app]} edn]
        (let [{:keys [inspector app]} app
              app-id (app-id (:reconciler app))]
          (om/transact! (:reconciler inspector) [::f.ui.network/history-id [::app-id app-id]]
            [`(f.ui.network/request-start ~{::f.ui.network/request-id  request-id
                                            ::f.ui.network/request-edn edn})]))
        edn)

      ::transform-response
      (fn [{::keys [request-id app]} response]
        (let [{:keys [inspector app]} app
              app-id (app-id (:reconciler app))]
          (om/transact! (:reconciler inspector) [::f.ui.network/history-id [::app-id app-id]]
            [`(f.ui.network/request-update ~{::f.ui.network/request-id   request-id
                                             ::f.ui.network/response-edn response})]))
        response)

      ::transform-error
      (fn [{::keys [request-id app]} error]
        (let [{:keys [inspector app]} app
              app-id (app-id (:reconciler app))]
          (om/transact! (:reconciler inspector) [::f.ui.network/history-id [::app-id app-id]]
            [`(f.ui.network/request-update ~{::f.ui.network/request-id request-id
                                             ::f.ui.network/error      error})]))
        error)})))

;;; installer

(defn inc-id [id]
  (let [new-id (if-let [[_ prefix d] (re-find #"(.+?)(\d+)$" (str id))]
                 (str prefix (inc (js/parseInt d)))
                 (str id "-0"))]
    (cond
      (keyword? id) (keyword (subs new-id 1))
      (symbol? id) (symbol new-id)
      :else new-id)))

(defn dedupe-id [id]
  (let [ids-in-use (some-> (global-inspector) :reconciler om/app-state deref ::inspector-id)]
    (loop [new-id id]
      (if (contains? ids-in-use new-id)
        (recur (inc-id new-id))
        new-id))))

(defn install [options]
  (when-not @global-inspector*
    (js/console.log "Installing Fulcro Inspect" options)
    (global-inspector options)

    (fulcro/register-tool
      {::fulcro/tool-id
       ::fulcro-inspect

       ::fulcro/app-started
       (fn [{:keys [reconciler] :as app}]
         (let [id (-> reconciler app-id dedupe-id)]
           (swap! (-> reconciler om/app-state) assoc ::app-id id)
           (inspect-app id app))
         app)

       ::fulcro/network-wrapper
       (fn [networks]
         (into {} (map (fn [[k v]] [k (inspect-network v)])) networks))

       ::fulcro/tx-listen
       (fn [{:keys [reconciler]} info]
         #_(js/console.log "tx" (app-id reconciler) info))})))
