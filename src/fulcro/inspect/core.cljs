(ns fulcro.inspect.core
  (:require
    [cljs.reader :refer [read-string]]
    [goog.functions :as gfun]
    [goog.object :as gobj]
    [fulcro.client :as fulcro]
    [fulcro.client.mutations :as mutations :refer-macros [defmutation]]
    [fulcro.client.network :as f.network]
    [fulcro.inspect.ui.data-viewer :as data-viewer]
    [fulcro.inspect.ui.data-watcher :as data-watcher]
    [fulcro.inspect.ui.inspector :as inspector]
    [fulcro.inspect.ui.multi-inspector :as multi-inspector]
    [fulcro.inspect.ui.events :as events]
    [fulcro.inspect.ui.element :as element]
    [fulcro.inspect.ui.network :as network]
    [fulcro.inspect.ui.transactions :as transactions]
    [fulcro-css.css :as css]
    [fulcro.client.dom :as dom]
    [fulcro.client.primitives :as fp]
    [garden.core :as g]))

(defn set-style! [node prop value]
  (gobj/set (gobj/get node "style") prop value))

(defn update-frame-content [this child]
  (let [frame-component (gobj/get this "frame-component")]
    (when frame-component
      (js/ReactDOM.render child frame-component))))

(defn start-frame [this]
  (let [frame-body (.-body (.-contentDocument (js/ReactDOM.findDOMNode this)))
        {:keys [child]} (fp/props this)
        e1         (.createElement js/document "div")]
    (when (= 0 (gobj/getValueByKeys frame-body #js ["children" "length"]))
      (.appendChild frame-body e1)
      (gobj/set this "frame-component" e1)
      (update-frame-content this child))))

(fp/defui IFrame
  Object
  (componentDidMount [this] (start-frame this))

  (componentDidUpdate [this _ _]
    (let [child (:child (fp/props this))]
      (update-frame-content this child)))

  (render [this]
    (dom/iframe
      (-> (fp/props this)
          (dissoc :child)
          (assoc :onLoad #(start-frame this))
          clj->js))))

(let [factory (fp/factory IFrame)]
  (defn ui-iframe [props child]
    (factory (assoc props :child child))))

(fp/defui ^:once GlobalInspector
  static fp/InitialAppState
  (initial-state [_ params] {:ui/size      50
                             :ui/visible?  false
                             :ui/inspector (fp/get-initial-state multi-inspector/MultiInspector params)})

  static fp/Ident
  (ident [_ props] [::floating-panel "main"])

  static fp/IQuery
  (query [_] [{:ui/inspector (fp/get-query multi-inspector/MultiInspector)}
              :ui/size :ui/visible?])

  static css/CSS
  (local-rules [_] [[:.container {:background "#fff"
                                  :box-shadow "rgba(0, 0, 0, 0.3) 0px 0px 4px"
                                  :position   "fixed"
                                  :top        "0"
                                  :right      "0"
                                  :bottom     "0"
                                  :width      "50%"
                                  :overflow   "hidden"
                                  :z-index    "9999999"}]
                    [:.resizer {:position    "fixed"
                                :cursor      "ew-resize"
                                :top         "0"
                                :left        "50%"
                                :margin-left "-5px"
                                :width       "10px"
                                :bottom      "0"
                                :z-index     "99999"}]
                    [:.frame {:width  "100%"
                              :height "100%"
                              :border "0"}]])
  (include-children [_] [element/MarkerCSS])

  Object
  (componentDidMount [this]
    (gobj/set this "frame-dom" (js/ReactDOM.findDOMNode (gobj/get this "frame-node")))
    (gobj/set this "resize-debouncer"
      (gfun/debounce #(mutations/set-value! this :ui/size %) 300)))

  (componentDidUpdate [this _ _]
    (gobj/set this "frame-dom" (js/ReactDOM.findDOMNode (gobj/get this "frame-node"))))

  (render [this]
    (let [{:ui/keys [size visible? inspector]} (fp/props this)
          keystroke (or (fp/shared this [:options :launch-keystroke]) "ctrl-f")
          size      (or size 50)
          css       (css/get-classnames GlobalInspector)]
      (dom/div #js {:className (:reset css)
                    :style     (if visible? nil #js {:display "none"})}
        (events/key-listener {::events/action    #(mutations/set-value! this :ui/visible? (not visible?))
                              ::events/keystroke keystroke})
        (dom/div #js {:className   (:resizer css)
                      :ref         #(gobj/set this "resizer" %)
                      :style       #js {:left (str size "%")}
                      :onMouseDown (fn [_]
                                     (let [handler (fn [e]
                                                     (let [mouse (.-clientX e)
                                                           vw    js/document.body.clientWidth
                                                           pos   (* (/ mouse vw) 100)]
                                                       (when (pos? pos)
                                                         (set-style! (gobj/get this "resizer") "left" (str pos "%"))
                                                         (set-style! (gobj/get this "container") "width" (str (- 100 pos) "%"))
                                                         ((gobj/get this "resize-debouncer") pos))))
                                           frame   (js/ReactDOM.findDOMNode (gobj/get this "frame-node"))]
                                       (set-style! frame "pointerEvents" "none")
                                       (js/document.addEventListener "mousemove" handler)
                                       (js/document.addEventListener "mouseup"
                                         (fn [e]
                                           (gobj/set (.-style frame) "pointerEvents" "initial")
                                           (js/document.removeEventListener "mousemove" handler)))))})
        (dom/div #js {:className (:container css)
                      :style     #js {:width (str (- 100 size) "%")}
                      :ref       #(gobj/set this "container" %)}
          (ui-iframe {:className (:frame css) :ref #(gobj/set this "frame-node" %)}
            (dom/div nil
              (when-let [frame (gobj/get this "frame-dom")]
                (events/key-listener {::events/action    #(mutations/set-value! this :ui/visible? (not visible?))
                                      ::events/keystroke keystroke
                                      ::events/target    (gobj/getValueByKeys frame #js ["contentDocument" "body"])}))
              (dom/style #js {:dangerouslySetInnerHTML #js {:__html (g/css [[:body {:margin "0" :padding "0" :box-sizing "border-box"}]])}})
              (dom/style #js {:dangerouslySetInnerHTML #js {:__html (g/css (css/get-css multi-inspector/MultiInspector))}})
              (multi-inspector/multi-inspector inspector))))))))

(def global-inspector-view (fp/factory GlobalInspector))

(fp/defui ^:once GlobalRoot
  static fp/InitialAppState
  (initial-state [_ _] {:ui/react-key (random-uuid)
                        :ui/root      (fp/get-initial-state GlobalInspector {})})

  static fp/IQuery
  (query [_] [{:ui/root (fp/get-query GlobalInspector)}
              :ui/react-key])

  static css/CSS
  (local-rules [_] [])
  (include-children [_] [GlobalInspector])

  Object
  (render [this]
    (let [{:keys [ui/react-key ui/root]} (fp/props this)]
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
  (fp/transact! reconciler [::data-watcher/id [::app-id app-id]]
    [`(data-watcher/update-state ~state) ::data-viewer/content]))

(defn inspect-network-init [network app]
  (some-> network :options ::app* (reset! app)))

(defn inspect-app [app-id target-app]
  (let [inspector     (global-inspector)
        state*        (some-> target-app :reconciler :config :state)
        new-inspector (-> (fp/get-initial-state inspector/Inspector @state*)
                          (assoc ::inspector/id app-id)
                          (assoc-in [::inspector/app-state ::data-watcher/id] [::app-id app-id])
                          (assoc-in [::inspector/network ::network/history-id] [::app-id app-id])
                          (assoc-in [::inspector/element ::element/panel-id] [::app-id app-id])
                          (assoc-in [::inspector/element ::element/target-reconciler] (:reconciler target-app))
                          (assoc-in [::inspector/transactions ::transactions/tx-list-id] [::app-id app-id]))]
    (fp/transact! (:reconciler inspector) [::multi-inspector/multi-inspector "main"]
      [`(multi-inspector/add-inspector ~new-inspector)
       ::inspectors])

    (inspect-network-init (-> target-app :networking :remote) {:inspector inspector
                                                               :app       target-app})

    (add-watch state* app-id
      #(update-inspect-state (:reconciler inspector) app-id %4))

    new-inspector))

;;; network

(defrecord TransformNetwork [network options]
  f.network/NetworkBehavior
  (serialize-requests? [this]
    (try
      (f.network/serialize-requests? network)
      (catch :default _ true)))

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
    (try
      (f.network/start network)
      (catch ::default e
        (js/console.log "Error starting sub network" e)))
    this))

(defn transform-network [network options]
  (->TransformNetwork network (assoc options ::app* (atom nil))))

(defn app-id [reconciler]
  (or (some-> reconciler fp/app-state deref ::app-id)
      (some-> reconciler fp/app-root fp/react-type (gobj/get "displayName") symbol)))

(defn inspect-network
  ([remote network]
   (transform-network network
     {::transform-query
      (fn [{::keys [request-id app]} edn]
        (let [{:keys [inspector app]} app
              app-id (app-id (:reconciler app))]
          (fp/transact! (:reconciler inspector) [::network/history-id [::app-id app-id]]
            [`(network/request-start ~{::network/remote      remote
                                       ::network/request-id  request-id
                                       ::network/request-edn edn})]))
        edn)

      ::transform-response
      (fn [{::keys [request-id app]} response]
        (let [{:keys [inspector app]} app
              app-id (app-id (:reconciler app))]
          (fp/transact! (:reconciler inspector) [::network/history-id [::app-id app-id]]
            [`(network/request-finish ~{::network/request-id   request-id
                                        ::network/response-edn response})]))
        response)

      ::transform-error
      (fn [{::keys [request-id app]} error]
        (let [{:keys [inspector app]} app
              app-id (app-id (:reconciler app))]
          (fp/transact! (:reconciler inspector) [::network/history-id [::app-id app-id]]
            [`(network/request-finish ~{::network/request-id request-id
                                        ::network/error      error})]))
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
  (let [ids-in-use (some-> (global-inspector) :reconciler fp/app-state deref ::inspector/id)]
    (loop [new-id id]
      (if (contains? ids-in-use new-id)
        (recur (inc-id new-id))
        new-id))))

(defn inspect-tx [{:keys [reconciler] :as env} info]
  (let [inspector (global-inspector)
        tx        (merge info (select-keys env [:old-state :new-state :ref :component]))
        app-id    (app-id reconciler)]
    (fp/transact! (:reconciler inspector) [::transactions/tx-list-id [::app-id app-id]]
      [`(transactions/add-tx ~tx) ::transactions/tx-list])))

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
           (swap! (-> reconciler fp/app-state) assoc ::app-id id)
           (inspect-app id app))
         app)

       ::fulcro/network-wrapper
       (fn [networks]
         (into {} (map (fn [[k v]] [k (inspect-network k v)])) networks))

       ::fulcro/tx-listen
       #'inspect-tx})))
