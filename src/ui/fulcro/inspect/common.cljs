(ns fulcro.inspect.common
  (:require
    [cljs.core.async :refer [<! go put!]]
    [com.fulcrologic.fulcro-css.css-injection :as cssi]
    [com.fulcrologic.fulcro-css.localized-dom :as dom]
    [com.fulcrologic.fulcro-i18n.i18n :as fulcro-i18n]
    [com.fulcrologic.fulcro.algorithms.normalize :as fnorm]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as fp]
    [com.fulcrologic.fulcro.inspect.transit :as encode]
    [com.fulcrologic.fulcro.mutations :as fm]
    [fulcro.inspect.lib.history :as hist]
    [fulcro.inspect.lib.local-storage :as storage]
    [fulcro.inspect.devtool-api-impl]
    [fulcro.inspect.ui-parser :as ui-parser]
    [fulcro.inspect.ui.data-watcher :as data-watcher]
    [fulcro.inspect.ui.element :as element]
    [fulcro.inspect.ui.i18n :as i18n]
    [fulcro.inspect.ui.inspector :as inspector]
    [fulcro.inspect.ui.multi-inspector :as multi-inspector]
    [goog.functions :refer [debounce]]
    [goog.object :as gobj]
    [taoensso.timbre :as log]))

(defonce websockets? (volatile! false))
(defonce global-inspector* (atom nil))

(fp/defsc GlobalRoot [this {:keys [ui/root]}]
  {:initial-state (fn [params] {:ui/root
                                (-> (fp/get-initial-state multi-inspector/MultiInspector params)
                                  (assoc-in [::multi-inspector/settings :ui/hide-websocket?] (not @websockets?)))})
   :query         [{:ui/root (fp/get-query multi-inspector/MultiInspector)}]
   :css           (fn [] (if @websockets?
                           [[:body {:margin "0" :padding "0" :box-sizing "border-box"}]]
                           [[:html {:overflow "hidden"}]
                            [:body {:margin "0" :padding "0" :box-sizing "border-box"}]]))
   :css-include   [multi-inspector/MultiInspector]}
  (dom/div
    (cssi/style-element {:component this})
    (multi-inspector/multi-inspector root)))

(defn inspector-app-names []
  (some->> @global-inspector* app/current-state ::inspector/id vals
    (mapv ::inspector/name) set))

(defn inc-id [id]
  (let [new-id (if-let [[_ prefix d] (re-find #"(.+?)(\d+)$" (str id))]
                 (str prefix (inc (js/parseInt d)))
                 (str id "-0"))]
    (cond
      (keyword? id) (keyword (subs new-id 1))
      (symbol? id) (symbol new-id)
      :else new-id)))

(defn dedupe-name [name]
  (let [names-in-use (inspector-app-names)]
    (loop [new-name name]
      (if (contains? names-in-use new-name)
        (recur (inc-id new-name))
        new-name))))

(defn reset-inspector []
  (-> @global-inspector* ::app/state-atom (reset! (fnorm/tree->db GlobalRoot (fp/get-initial-state GlobalRoot {}) true))))

(defn client-connection-id "websocket only" [event] (some-> event (gobj/get "client-id")))

(defn event-data [event]
  (let [base-event   (some-> event (gobj/get "fulcro-inspect-remote-message") encode/read)
        ws-client-id (client-connection-id event)]
    (cond-> base-event
      ws-client-id (assoc-in [:data :fulcro.inspect.core/client-connection-id] ws-client-id))))

(defn respond-to-load! [responses* type data]
  (if-let [res-chan (get @responses* (::ui-parser/msg-id data))]
    (put! res-chan (::ui-parser/msg-response data))
    (log/error "Failed to respond locally to message:" type "with data:" data)))

(defn start-app* [inspector {app-uuid                    ::app/id
                             :fulcro.inspect.client/keys [initial-history-step remotes]}]
  (let [app-name      (get-in initial-history-step [:history/value :fulcro.inspect.core/app-id] (str app-uuid))
        new-inspector (-> (fp/get-initial-state inspector/Inspector {:id      app-uuid
                                                                     :remotes remotes})
                        (assoc ::inspector/name (dedupe-name app-name)) ; TODO
                        (assoc-in [::inspector/settings :ui/hide-websocket?] true)
                        (assoc-in [::inspector/app-state :data-history/watcher :data-watcher/watches]
                          (->> (storage/get [:data-watcher/watches app-uuid] [])
                            (mapv (fn [path]
                                    (fp/get-initial-state data-watcher/WatchPin
                                      {:path     path
                                       :id       app-uuid
                                       :expanded (storage/get [:data-watcher/watches-expanded app-uuid path] {})
                                       :content  (get-in (:history/value initial-history-step) path)})))))
                        #_(assoc-in [::inspector/element ::element/panel-id] [app-uuid-key app-uuid])
                        #_#_#_(assoc-in [::inspector/i18n ::i18n/id] [app-uuid-key app-uuid])
                                (assoc-in [::inspector/i18n ::i18n/current-locale] (-> (get-in initial-state (-> initial-state ::fulcro-i18n/current-locale))
                                                                                     ::fulcro-i18n/locale))
                                (assoc-in [::inspector/i18n ::i18n/locales] (->> initial-state ::fulcro-i18n/locale-by-id vals vec
                                                                              (mapv #(vector (::fulcro-i18n/locale %) (:ui/locale-name %)))))
                        )]

    (fp/transact! inspector [(multi-inspector/add-inspector new-inspector)]
      {:ref [::multi-inspector/multi-inspector "main"]})
    (fp/transact! inspector [(hist/save-history-step initial-history-step)])
    new-inspector))

(fm/defmutation start-app [params]
  (action [{:keys [app]}]
    (start-app* app params)))
