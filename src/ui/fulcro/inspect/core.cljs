(ns fulcro.inspect.core
  (:require
    [cljs.reader :refer [read-string]]
    [goog.functions :as gfun]
    [goog.object :as gobj]
    [fulcro.client :as fulcro]
    [fulcro.client.mutations :as mutations :refer-macros [defmutation]]
    [fulcro.client.network :as f.network]
    [fulcro.inspect.helpers :as db.h]
    [fulcro.inspect.lib.local-storage :as storage]
    [fulcro.inspect.ui.data-history :as data-history]
    [fulcro.inspect.ui.dom-history-viewer :as domv]
    [fulcro.inspect.ui.inspector :as inspector]
    [fulcro.inspect.ui.multi-inspector :as multi-inspector]
    [fulcro.inspect.ui.events :as events]
    [fulcro.inspect.ui.element :as element]
    [fulcro.inspect.ui.network :as network]
    [fulcro.inspect.ui.transactions :as transactions]
    [fulcro-css.css :as css]
    [fulcro.client.dom :as dom]
    [fulcro.client.primitives :as fp]
    [fulcro.inspect.ui.helpers :as ui.h]
    [cljs.spec.alpha :as s]))

(s/def ::app-id (s/or :string string? :keyword keyword? :sym symbol?))
(s/def ::app-uuid uuid?)

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

  (componentWillUnmount [this]
    (let [frame-component (gobj/get this "frame-component")]
      (js/ReactDOM.unmountComponentAtNode frame-component)))

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
  (initial-state [_ params] {:ui/size                (storage/get ::dock-size 50)
                             :ui/dock-side           (storage/get ::dock-side ::dock-right)
                             :ui/visible?            (storage/get ::dock-visible? false)
                             :ui/historical-dom-view (fp/get-initial-state domv/DOMHistoryView {})
                             :ui/inspector           (fp/get-initial-state multi-inspector/MultiInspector params)})

  static fp/Ident
  (ident [_ props] [::floating-panel "main"])

  static fp/IQuery
  (query [_] [{:ui/inspector (fp/get-query multi-inspector/MultiInspector)}
              {:ui/historical-dom-view (fp/get-query domv/DOMHistoryView)}
              :ui/size :ui/visible? :ui/dock-side])

  static css/CSS
  (local-rules [_] [[:.container {:background "#fff"
                                  :box-shadow "rgba(0, 0, 0, 0.3) 0px 0px 4px"
                                  :position   "fixed"
                                  :overflow   "hidden"
                                  :z-index    "9999999"}]
                    [:.container-right {:top    "0"
                                        :right  "0"
                                        :bottom "0"
                                        :width  "50%"}]
                    [:.container-bottom {:left   "0"
                                         :right  "0"
                                         :bottom "0"
                                         :height "50%"}]
                    [:.resizer {:position "fixed"
                                :z-index  "99999"}]
                    [:.resizer-horizontal {:cursor      "ew-resize"
                                           :top         "0"
                                           :left        "50%"
                                           :margin-left "-5px"
                                           :width       "10px"
                                           :bottom      "0"}]
                    [:.resizer-vertical {:cursor     "ns-resize"
                                         :left       "0"
                                         :top        "50%"
                                         :margin-top "-5px"
                                         :height     "10px"
                                         :right      "0"}]
                    [:.frame {:width  "100%"
                              :height "100%"
                              :border "0"}]])
  (include-children [_] [element/MarkerCSS domv/DOMHistoryView])

  Object
  (componentDidMount [this]
    (gobj/set this "frame-dom" (js/ReactDOM.findDOMNode (gobj/get this "frame-node")))
    (gobj/set this "resize-debouncer"
      (gfun/debounce #(db.h/persistent-set! this :ui/size ::dock-size %) 300)))

  (componentDidUpdate [this _ _]
    (gobj/set this "frame-dom" (js/ReactDOM.findDOMNode (gobj/get this "frame-node"))))

  (render [this]
    (let [{:ui/keys [size visible? inspector historical-dom-view dock-side]} (fp/props this)
          {:keys [::multi-inspector/current-app]} inspector
          app       (::inspector/target-app current-app)
          keystroke (or (fp/shared this [:options :launch-keystroke]) "ctrl-f")
          size      (or size 50)
          css       (css/get-classnames GlobalInspector)]
      (dom/div #js {:className (:reset css)}
        (events/key-listener {::events/action    #(db.h/persistent-set! this :ui/visible? ::dock-visible? (not visible?))
                              ::events/keystroke keystroke})
        (domv/ui-dom-history-view (fp/computed historical-dom-view {:target-app app}))

        (if visible?
          (case dock-side
            ::dock-right
            (dom/div #js {:className   (str (:resizer css) " " (:resizer-horizontal css))
                          :ref         #(gobj/set this "resizer" %)
                          :style       #js {:left (str size "%")}
                          :onMouseDown (fn [_]
                                         (let [handler (fn [e]
                                                         (let [mouse (.-clientX e)
                                                               vw    js/window.innerWidth
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

            ::dock-bottom
            (dom/div #js {:className   (str (:resizer css) " " (:resizer-vertical css))
                          :ref         #(gobj/set this "resizer" %)
                          :style       #js {:top (str size "%")}
                          :onMouseDown (fn [_]
                                         (let [handler (fn [e]
                                                         (let [mouse (.-clientY e)
                                                               vh    js/window.innerHeight
                                                               pos   (* (/ mouse vh) 100)]
                                                           (when (pos? pos)
                                                             (set-style! (gobj/get this "resizer") "top" (str pos "%"))
                                                             (set-style! (gobj/get this "container") "height" (str (- 100 pos) "%"))
                                                             ((gobj/get this "resize-debouncer") pos))))
                                               frame   (js/ReactDOM.findDOMNode (gobj/get this "frame-node"))]
                                           (set-style! frame "pointerEvents" "none")
                                           (js/document.addEventListener "mousemove" handler)
                                           (js/document.addEventListener "mouseup"
                                             (fn [e]
                                               (gobj/set (.-style frame) "pointerEvents" "initial")
                                               (js/document.removeEventListener "mousemove" handler)))))})))

        (if visible?
          (case dock-side
            ::dock-right
            (dom/div #js {:className (str (:container css) " " (:container-right css))
                          :style     #js {:width (str (- 100 size) "%")}
                          :ref       #(gobj/set this "container" %)}
              (ui-iframe {:className (:frame css) :ref #(gobj/set this "frame-node" %)}
                (multi-inspector/multi-inspector inspector)))

            ::dock-bottom
            (dom/div #js {:className (str (:container css) " " (:container-bottom css))
                          :style     #js {:height (str (- 100 size) "%")}
                          :ref       #(gobj/set this "container" %)}
              (ui-iframe {:className (:frame css) :ref #(gobj/set this "frame-node" %)}
                (multi-inspector/multi-inspector inspector)))))))))

(def global-inspector-view (fp/factory GlobalInspector))

(fp/defui ^:once GlobalRoot
  static fp/InitialAppState
  (initial-state [_ _] {:ui/react-key (random-uuid)
                        :ui/root      (fp/get-initial-state GlobalInspector {})})

  static fp/IQuery
  (query [_] [:ui/react-key {:ui/root (fp/get-query GlobalInspector)}])

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
  (fp/transact! reconciler [::data-history/history-id [::app-id app-id]]
    [`(data-history/set-content ~state) ::data-history/history]))

(defn inspect-network-init [network app]
  (some-> network :options ::app* (reset! app)))

(defn inspect-app [app-id target-app]
  (let [inspector     (global-inspector)
        state*        (some-> target-app :reconciler :config :state)
        new-inspector (-> (fp/get-initial-state inspector/Inspector @state*)
                          (assoc ::inspector/id app-id)
                          (assoc ::inspector/target-app target-app)
                          (assoc-in [::inspector/app-state ::data-history/history-id] [::app-id app-id])
                          (assoc-in [::inspector/app-state ::data-history/snapshots] (storage/tget [::data-history/snapshots (db.h/normalize-id app-id)] []))
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

    (swap! state* assoc ::initialized true)

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

(defrecord TransformNetworkI [network options]
  f.network/FulcroRemoteI
  (transmit [_ {::f.network/keys [edn ok-handler error-handler]}]
    (let [{::keys [transform-query transform-response transform-error app*]
           :or    {transform-query    (fn [_ x] x)
                   transform-response (fn [_ x] x)
                   transform-error    (fn [_ x] x)}} options
          req-id (random-uuid)
          env    {::request-id req-id
                  ::app        @app*}]
      (if-let [edn' (transform-query env edn)]
        (f.network/transmit network
          {::f.network/edn           edn'
           ::f.network/ok-handler    #(->> % (transform-response env) ok-handler)
           ::f.network/error-handler #(->> % (transform-error env) error-handler)})
        (ok-handler nil))))

  (abort [_ abort-id] (f.network/abort network abort-id)))

(defn transform-network-i [network options]
  (->TransformNetworkI network (assoc options ::app* (atom nil))))

(defn app-id [reconciler]
  (or (some-> reconciler fp/app-state deref ::app-id)
      (some-> reconciler fp/app-root (gobj/get "displayName") symbol)
      (some-> reconciler fp/app-root fp/react-type (gobj/get "displayName") symbol)))

(defn inspect-network
  ([remote network]
   (let [ts {::transform-query
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
               error)}]
     (cond
       (implements? f.network/FulcroNetwork network)
       (transform-network network ts)

       (implements? f.network/FulcroRemoteI network)
       (transform-network-i network
         (update ts ::transform-response (fn [tr] (fn [env {:keys [body] :as response}]
                                                    (tr env body)
                                                    response))))

       :else
       (js/console.warn "Invalid network" {:network network})))))

;;; installer

(defn inc-id [id]
  (let [new-id (if-let [[_ prefix d] (re-find #"(.+?)(\d+)$" (str id))]
                 (str prefix (inc (js/parseInt d)))
                 (str id "-0"))]
    (cond
      (keyword? id) (keyword (subs new-id 1))
      (symbol? id) (symbol new-id)
      :else new-id)))

(defn inspector-app-ids []
  (some-> (global-inspector) :reconciler fp/app-state deref ::inspector/id))

(defn dedupe-id [id]
  (let [ids-in-use (inspector-app-ids)]
    (loop [new-id id]
      (if (contains? ids-in-use new-id)
        (recur (inc-id new-id))
        new-id))))

(defn inspect-tx [{:keys [reconciler] :as env} info]
  (if (fp/app-root reconciler) ; ensure app is initialized
    (let [inspector (global-inspector)
          tx        (merge info (select-keys env [:old-state :new-state :ref :component]))
          app-id    (app-id reconciler)]
      (if (-> reconciler fp/app-state deref ::initialized)
        (fp/transact! (:reconciler inspector) [::transactions/tx-list-id [::app-id app-id]]
          [`(transactions/add-tx ~tx) ::transactions/tx-list])))))

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
