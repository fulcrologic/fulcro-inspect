(ns fulcro.inspect.remote
  (:require [fulcro.client :as fulcro]
            [fulcro.client.primitives :as fp]
            [fulcro.inspect.remote.transit :as encode]
            [goog.object :as gobj]
            [fulcro.inspect.ui.data-history :as data-history]))

(defonce started?* (atom false))
(defonce apps* (atom {}))

(def app-id-key :fulcro.inspect.core/app-id)

(defn chrome-extension-installed? []
  (js/document.documentElement.getAttribute "__fulcro-inspect-chrome-installed__"))

(defn post-message [type data]
  (.postMessage js/window #js {:fulcro-inspect-remote-message (encode/write {:type type :data data})} "*"))

(defn find-remote-server []
  )

(defn app-id [reconciler]
  (or (some-> reconciler fp/app-state deref app-id-key)
      (some-> reconciler fp/app-root (gobj/get "displayName") symbol)
      (some-> reconciler fp/app-root fp/react-type (gobj/get "displayName") symbol)))

(defn inc-id [id]
  (let [new-id (if-let [[_ prefix d] (re-find #"(.+?)(\d+)$" (str id))]
                 (str prefix (inc (js/parseInt d)))
                 (str id "-0"))]
    (cond
      (keyword? id) (keyword (subs new-id 1))
      (symbol? id) (symbol new-id)
      :else new-id)))

(defn dedupe-id [id]
  (let [ids-in-use @apps*]
    (loop [new-id id]
      (if (contains? ids-in-use new-id)
        (recur (inc-id new-id))
        new-id))))

(defn inspect-network-init [network app]
  (some-> network :options ::app* (reset! app)))

(defn transact! [])

(gobj/set js/window "PING_PORT"
  (fn []
    (post-message ::ping {:msg-id (random-uuid)})))

(defn update-inspect-state [app-id state]
  (post-message ::transact-client {::tx-ref [::data-history/history-id [app-id-key app-id]]
                                   ::tx     [`(data-history/set-content ~state) ::data-history/history]}))

(defn inspect-app [target-app]
  (let [state* (some-> target-app :reconciler :config :state)
        app-id (app-id (:reconciler target-app))]
    #_(inspect-network-init (-> target-app :networking :remote) {:inspector inspector
                                                                 :app       target-app})

    (add-watch state* app-id
      #(update-inspect-state app-id %4))

    (swap! state* assoc ::initialized true)
    #_new-inspector))

(defn install [_]
  (when-not @started?*
    (js/console.log "Installing Fulcro Inspect" {})

    (reset! started?* true)

    (fulcro/register-tool
      {::fulcro/tool-id
       ::fulcro-inspect-remote

       ::fulcro/app-started
       (fn [{:keys [reconciler] :as app}]
         (let [state* (some-> reconciler fp/app-state)]
           (js/console.log "POST!!")
           (post-message ::init-app {app-id-key      (app-id reconciler)
                                     ::message-id    (random-uuid)
                                     ::initial-state @state*})
           (inspect-app app))
         app)

       #_#_::fulcro/network-wrapper
           (fn [networks]
             (into {} (map (fn [[k v]] [k (inspect-network k v)])) networks))

       #_#_::fulcro/tx-listen
           #'inspect-tx})))
