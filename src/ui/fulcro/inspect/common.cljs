(ns fulcro.inspect.common
  (:require
    [cljs.core.async :refer [<! go put!]]
    [com.fulcrologic.devtools.common.message-keys :as mk]
    [com.fulcrologic.fulcro-css.css-injection :as cssi]
    [com.fulcrologic.fulcro-css.localized-dom :as dom]
    [com.fulcrologic.fulcro-i18n.i18n :as fulcro-i18n]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.algorithms.normalize :as fnorm]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as fp]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.inspect.devtool-api :as tapi]
    [com.fulcrologic.fulcro.mutations :as fm]
    [fulcro.inspect.helpers :as h]
    [fulcro.inspect.lib.diff :as diff]
    [fulcro.inspect.lib.history :as hist]
    [fulcro.inspect.lib.local-storage :as storage]
    [fulcro.inspect.lib.version :as version]
    [fulcro.inspect.remote.transit :as encode]
    [fulcro.inspect.ui-parser :as ui-parser]
    [fulcro.inspect.ui.data-history :as data-history]
    [fulcro.inspect.ui.data-watcher :as data-watcher]
    [fulcro.inspect.ui.db-explorer :as db-explorer]
    [fulcro.inspect.ui.element :as element]
    [fulcro.inspect.ui.i18n :as i18n]
    [fulcro.inspect.ui.inspector :as inspector]
    [fulcro.inspect.ui.multi-inspector :as multi-inspector]
    [fulcro.inspect.ui.multi-oge :as multi-oge]
    [fulcro.inspect.ui.network :as network]
    [fulcro.inspect.ui.statecharts :as statecharts]
    [fulcro.inspect.ui.transactions :as transactions]
    [com.fulcrologic.devtools.common.resolvers :as dres]
    [com.wsscode.pathom.connect :as pc]
    [goog.functions :refer [debounce]]
    [goog.object :as gobj]
    [taoensso.timbre :as log]))

(defonce websockets? (volatile! false))
(defonce global-inspector* (atom nil))
(defonce last-disposed-app* (atom nil))

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

(defn dispose-app [{::app/keys [id] :as params}]
  (let [app           @global-inspector*
        state         (app/current-state app)
        inspector-ref [::inspector/id id]]

    (if (= (get-in @state [::multi-inspector/multi-inspector "main" ::multi-inspector/current-app])
          inspector-ref)
      (reset! last-disposed-app* id)
      (reset! last-disposed-app* nil))

    (fp/transact! app [(hist/clear-history params)])
    (fp/transact! app [(multi-inspector/remove-inspector params)]
      {:ref [::multi-inspector/multi-inspector "main"]})))

(defn reset-inspector []
  (-> @global-inspector* ::app/state-atom (reset! (fnorm/tree->db GlobalRoot (fp/get-initial-state GlobalRoot {}) true))))

(defn tx-run [{:fulcro.inspect.client/keys [tx tx-ref]}]
  (let [app @global-inspector*]
    (if tx-ref
      (fp/transact! app tx {:ref tx-ref})
      (fp/transact! app tx))))

(defonce last-step-filled (volatile! nil))
(defn- -fill-last-entry!
  []
  (let [app       @global-inspector*
        state-map (app/current-state app)
        app-uuid  (h/current-app-uuid state-map)
        version   (hist/latest-state-version app app-uuid)]
    (when (not= @last-step-filled version)
      (do
        (vreset! last-step-filled version)
        (hist/fetch-history-step! app version)))))

(def fill-last-entry!
  "Request the full state for the currently-selected application"
  (debounce -fill-last-entry! 5))

(dres/defmutation send-started [env params]
  {::pc/sym `tapi/send-started}
  (log/info "Send started" params)
  nil)

(dres/defmutation send-finished [env params]
  {::pc/sym `tapi/send-finished}
  (log/info "Send finished" params)
  nil)

(dres/defmutation send-failed [env params]
  {::pc/sym `tapi/send-failed}
  (log/info "Send failed" params)
  nil)

(dres/defmutation optimistic-action [env params]
  {::pc/sym `tapi/optimistic-action}
  (log/info "Optimistic action" params)
  nil)

(dres/defmutation update-client-db [env {::app/keys    [id]
                                         :history/keys [version value] :as history-step}]
  {::pc/sym `tapi/db-changed}
  (let [app @global-inspector*]
    (log/info "DB change" history-step)
    (fp/transact! app [(hist/save-history-step history-step)])
    #_(fp/transact! app [(db-explorer/set-current-state step)] {:ref [::db-explorer/id [::app/id app-uuid]]})
    nil))

(defn new-client-tx [{:fulcro.inspect.core/keys   [app-uuid]
                      :fulcro.inspect.client/keys [tx]}]
  (let [{:fulcro.history/keys [db-before-id
                               db-after-id]} tx
        inspector @global-inspector*
        tx        (assoc tx
                    :fulcro.history/db-before (hist/history-step inspector app-uuid db-before-id)
                    :fulcro.history/db-after (hist/history-step inspector app-uuid db-after-id))]
    (fp/transact! inspector
      [(fulcro.inspect.ui.transactions/add-tx tx)]
      {:ref [:fulcro.inspect.ui.transactions/tx-list-id [::app/id app-uuid]]})))

(defn client-connection-id "websocket only" [event] (some-> event (gobj/get "client-id")))

(defn event-data [event]
  (let [base-event   (some-> event (gobj/get "fulcro-inspect-remote-message") encode/read)
        ws-client-id (client-connection-id event)]
    (cond-> base-event
      ws-client-id (assoc-in [:data :fulcro.inspect.core/client-connection-id] ws-client-id))))

(defn set-active-app [{:fulcro.inspect.core/keys [app-uuid]}]
  (let [inspector @global-inspector*]
    (fp/transact! inspector [(multi-inspector/set-app {::inspector/id app-uuid})]
      {:ref [::multi-inspector/multi-inspector "main"]})))

(defn notify-stale-app []
  (let [inspector @global-inspector*]
    (fp/transact! inspector [(fm/set-props {::multi-inspector/client-stale? true})]
      {:ref [::multi-inspector/multi-inspector "main"]})))

(defn fill-history-entry
  "Called in response to the client sending us the real state for a given state id, at which time we update
   our copy of the history with the new value"
  [{:fulcro.inspect.core/keys   [app-uuid state-id]
    :fulcro.inspect.client/keys [diff based-on state]}]
  (let [inspector @global-inspector*
        state     (if state
                    state
                    (let [base-state (hist/version-of-state-map inspector app-uuid based-on)]
                      (when-not base-state
                        (log/error "Cannot build a new history state because there was no base state"))
                      (diff/patch base-state diff)))]
    ;(hist/record-history-step! inspector app-uuid {:id state-id :value state})
    (app/force-root-render! inspector)))

(defn respond-to-load! [responses* type data]
  (if-let [res-chan (get @responses* (::ui-parser/msg-id data))]
    (put! res-chan (::ui-parser/msg-response data))
    (log/error "Failed to respond locally to message:" type "with data:" data)))

(defn start-app [{app-uuid                    ::app/id
                  :fulcro.inspect.client/keys [initial-history-step remotes]}]
  (let [inspector     @global-inspector*
        new-inspector (-> (fp/get-initial-state inspector/Inspector {:id      app-uuid
                                                                     :remotes remotes})
                        (assoc ::inspector/name (dedupe-name app-uuid)) ; TODO
                        (assoc-in [::inspector/settings :ui/hide-websocket?] true)
                        (assoc-in [::inspector/app-state :data-history/watcher :data-watcher/watches]
                          (->> (storage/get [:data-watcher/watches app-uuid] [])
                            (mapv (fn [path]
                                    (fp/get-initial-state data-watcher/WatchPin
                                      {:path     path
                                       :id app-uuid
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

    (when (= app-uuid @last-disposed-app*)
      (reset! last-disposed-app* nil)
      (fp/transact! inspector
        [(multi-inspector/set-app {::inspector/id app-uuid})]
        {:ref [::multi-inspector/multi-inspector "main"]}))

    (fp/transact! inspector
      [(db-explorer/set-current-state initial-history-step)]
      {:ref [::db-explorer/id [::app/id app-uuid]]})
    (fp/transact! inspector
      [(data-history/set-content initial-history-step)]
      {:ref [:data-history/id [::app/id app-uuid]]})

    new-inspector))

(dres/defmutation app-started [env params]
  {::pc/sym `tapi/app-started}
  (log/info "App started: " params)
  (start-app params)
  nil)
