(ns fulcro.inspect.remote
  (:require [fulcro.client :as fulcro]
            [fulcro.client.primitives :as fp]
            [fulcro.inspect.remote.transit :as encode]
            [goog.object :as gobj]
            [fulcro.inspect.ui.data-history :as data-history]))

(defonce started?* (atom false))
(defonce apps* (atom {}))

(def app-id-key :fulcro.inspect.core/app-id)

(defn post-message [type data]
  (.postMessage js/window #js {:fulcro-inspect-remote-message (encode/write {:type type :data data :timestamp (js/Date.)})} "*"))

(defn listen-local-messages []
  (.addEventListener js/window "message"
    (fn [event]
      (when (and (= (.-source event) js/window)
                 (gobj/getValueByKeys event "data" "fulcro-inspect-devtool-message"))
        (js/console.log "DEVTOOL MESSAGE" event)))
    false))

(defn find-remote-server []
  )

(defn app-name [reconciler]
  (or (some-> reconciler fp/app-state deref app-id-key)
      (some-> reconciler fp/app-root (gobj/get "displayName") symbol)
      (some-> reconciler fp/app-root fp/react-type (gobj/get "displayName") symbol)))

(defn inspect-network-init [network app]
  (some-> network :options ::app* (reset! app)))

(defn transact!
  ([tx]
   (post-message ::transact-client {::tx tx}))
  ([ref tx]
   (post-message ::transact-client {::tx-ref ref ::tx tx})))

(gobj/set js/window "PING_PORT"
  (fn []
    (post-message ::ping {:msg-id (random-uuid)})))

(defn update-inspect-state [app-id state]
  (transact! [::data-history/history-id [app-id-key app-id]]
             [`(data-history/set-content ~state) ::data-history/history]))

(defn inspect-app [target-app]
  (let [state* (some-> target-app :reconciler :config :state)
        app-id (app-name (:reconciler target-app))]
    #_(inspect-network-init (-> target-app :networking :remote) {:inspector inspector
                                                                 :app       target-app})

    (add-watch state* app-id
      #(update-inspect-state app-id %4))

    (swap! state* assoc ::initialized true)
    #_new-inspector))

(defn install [_]
  (js/document.documentElement.setAttribute "__fulcro-inspect-remote-installed__" true)

  (when-not @started?*
    (js/console.log "Installing Fulcro Inspect" {})

    (reset! started?* true)

    (fulcro/register-tool
      {::fulcro/tool-id
       ::fulcro-inspect-remote

       ::fulcro/app-started
       (fn [{:keys [reconciler] :as app}]
         (let [state* (some-> reconciler fp/app-state)
               app-id (random-uuid)]
           (post-message ::init-app {app-id-key      app-id
                                     ::app-name      (app-name reconciler)
                                     ::initial-state @state*})

           (swap! apps* assoc app-id app)
           (swap! state* assoc app-id-key app-id)

           (inspect-app app))
         app)

       #_#_::fulcro/network-wrapper
           (fn [networks]
             (into {} (map (fn [[k v]] [k (inspect-network k v)])) networks))

       #_#_::fulcro/tx-listen
           #'inspect-tx})

    (listen-local-messages)))
