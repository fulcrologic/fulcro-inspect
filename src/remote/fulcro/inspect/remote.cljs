(ns fulcro.inspect.remote
  (:require [fulcro.client :as fulcro]
            [fulcro.client.primitives :as fp]
            [fulcro.inspect.remote.transit :as encode]
            [goog.object :as gobj]))

(defonce started?* (atom false))
(defonce apps* (atom {}))

(defn chrome-extension-installed? []
  (js/document.documentElement.getAttribute "__fulcro-inspect-chrome-installed__"))

(defn post-message [type data]
  (.postMessage js/window #js {:type type :data (encode/write data)} "*"))

(defn find-remote-server []
  )

(defn app-id [reconciler]
  (or (some-> reconciler fp/app-state deref :fulcro.inspect.core/app-id)
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

(defn inspect-app [app-id target-app]
  (let [state* (some-> target-app :reconciler :config :state)]
    #_(inspect-network-init (-> target-app :networking :remote) {:inspector inspector
                                                                 :app       target-app})

    #_(add-watch state* app-id
        #(update-inspect-state (:reconciler inspector) app-id %4))

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
           (post-message "fulcro-inspect-app-start" {::app-id        (app-id reconciler)
                                                     ::initial-state @state*}))

         #_(let [id (-> reconciler app-id dedupe-id)]
             (swap! (-> reconciler fp/app-state) assoc :fulcro.inspect.core/app-id id)
             (inspect-app id app))
         app)

       #_#_::fulcro/network-wrapper
           (fn [networks]
             (into {} (map (fn [[k v]] [k (inspect-network k v)])) networks))

       #_#_::fulcro/tx-listen
           #'inspect-tx})))
