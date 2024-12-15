(ns fulcro.inspect.api.target-impl
  (:require
    [clojure.core.async :as async]
    [com.fulcrologic.devtools.common.built-in-mutations :as bi]
    [com.fulcrologic.devtools.common.resolvers :refer [defmutation defresolver]]
    [com.fulcrologic.devtools.common.utils :refer [strip-lambdas]]
    [com.fulcrologic.devtools.devtool-io :as dio]
    [com.fulcrologic.fulcro.algorithms.lookup :as ah]
    [com.fulcrologic.fulcro.inspect.devtool-api :as devtool]
    [com.fulcrologic.fulcro.inspect.diff :as diff]
    [com.fulcrologic.fulcro.inspect.inspect-client
     :refer [app-state app-uuid app-uuid-key earliest-history-step get-history-entry
             record-history-entry! remotes runtime-atom send-failed! send-finished! send-started! state-atom]]
    [com.fulcrologic.fulcro.raw.components :as rc]
    [edn-query-language.core :as eql]
    [fulcro.inspect.api.target-api :as target]
    [taoensso.encore :as enc]
    [taoensso.timbre :as log]))


(defmulti handle-inspect-event (fn [app {:event/keys [symbol] :as event}] symbol))

(defmethod handle-inspect-event `devtool/db-changed [app event] (rc/transact! app [(devtool/db-changed event)]))

(defmethod handle-inspect-event `devtool/send-started [app event]
  (let [app-uuid (app-uuid app)]
    (dio/transact! app app-uuid
      [(devtool/send-started (assoc event :fulcro.inspect.client/tx-ref [:fulcro.inspect.ui.network/history-id [app-uuid-key app-uuid]]))])))

(defmethod handle-inspect-event `devtool/send-finished [app event]
  (let [app-uuid (app-uuid app)]
    (dio/transact! app app-uuid
      [(devtool/send-finished (assoc event :fulcro.inspect.client/tx-ref [:fulcro.inspect.ui.network/history-id [app-uuid-key app-uuid]]))])))

(defmethod handle-inspect-event `devtool/send-failed [app event]
  (let [app-uuid (app-uuid app)]
    (dio/transact! app app-uuid
      [(devtool/send-failed (assoc event :fulcro.inspect.client/tx-ref [:fulcro.inspect.ui.network/history-id [app-uuid-key app-uuid]]))])))

(defmethod handle-inspect-event `devtool/optimistic-action [app event]
  (let [app-uuid (app-uuid app)]
    (dio/transact! app app-uuid
      [(devtool/optimistic-action (assoc event :fulcro.inspect.client/tx-ref [:fulcro.inspect.ui.network/history-id [app-uuid-key app-uuid]]))])))

(defonce apps* (atom {}))

(defmutation connected [{:fulcro/keys [app]} input]
  {::pc/sym `bi/devtool-connected}
  (let [networking (remotes app)
        state*     (state-atom app)]
    (dio/transact! app (app-uuid app) [(devtool/app-started {:fulcro.inspect.client/remotes    (sort-by (juxt #(not= :remote %) str) (keys networking))
                                                          :fulcro.inspect.client/initial-state @state*})])))

(defresolver page-apps-resolver [env input]
  {::pc/output [{:page/apps [app-uuid-key
                             :fulcro.inspect.client/remotes
                             {:fulcro.inspect.client/initial-history-step [app-uuid-key
                                                                           :history/value
                                                                           :history/version]}]}]}
  {:page/apps
   (mapv
     (fn [app]
       (let [state        (app-state app)
             state-id     (record-history-entry! app state)
             remote-names (remotes app)]
         {app-uuid-key                                (app-uuid app)
          :fulcro.inspect.client/remotes              (sort-by (juxt #(not= :remote %) str) (keys remote-names))
          :fulcro.inspect.client/initial-history-step {app-uuid-key     (app-uuid app)
                                                       :history/version state-id
                                                       :history/value   state}}))
     (vals @apps*))})

(defmutation reset-app [{:fulcro/keys [app]} data]
  {::pc/sym `target/reset-app}
  (let [{:keys [target-state-id]} data
        app-uuid (app-uuid data)]
    (if-let [app (get @apps* app-uuid)]
      (let [render! (ah/app-algorithm app :schedule-render!)]
        (if target-state-id
          (enc/when-let [app (get @apps* app-uuid)
                         {:keys [value]} (get-history-entry app target-state-id)]
            (reset! (state-atom app) value))
          (log/error "Reset failed. No target state ID supplied"))
        (render! app {:force-root? true}))
      (log/info "Reset app on invalid uuid" app-uuid))
    nil))

(defresolver history-step-resolver [env input]
  {::pc/output [{[] [app-uuid-key
                     :fulcro.inspect.core/state-id
                     :fulcro.inspect.client/diff
                     :fulcro.inspect.client/based-on
                     :fulcro.inspect.client/state]}]}
  (let [params   (:query-params env)
        {:keys [id based-on]} params
        app-uuid (app-uuid params)]
    (enc/if-let [app (get @apps* app-uuid)
                 id  (or id (earliest-history-step app))
                 {:keys [value]} (get-history-entry app id)]
      (let [{prior-state :value} (get-history-entry app based-on)
            diff (when prior-state (diff/diff prior-state value))]
        (cond-> {app-uuid-key                  app-uuid
                 :fulcro.inspect.core/state-id id}
          diff (assoc :fulcro.inspect.client/diff diff
                      :fulcro.inspect.client/based-on based-on)
          (not diff) (assoc :fulcro.inspect.client/state value)))
      (log/error "Failed to resolve history step."))))

(defmutation run-transaction [env params]
  {::pc/sym `target/run-transaction}
  (let [{:keys [tx tx-ref]} params
        app-uuid (app-uuid params)]
    (if-let [app (get @apps* app-uuid)]
      (if tx-ref
        (rc/transact! app tx {:ref tx-ref})
        (rc/transact! app tx {}))
      (log/error "Transact on invalid uuid" app-uuid "See https://book.fulcrologic.com/#err-inspect-invalid-app-uuid"))
    nil))

(defmutation run-network-request [env params]
  {::pc/sym `target/run-network-request}
  (let [{:keys                          [query mutation]
         remote-name                    :fulcro.inspect.client/remote
         :fulcro.inspect.ui-parser/keys [msg-id]} params
        app-uuid (app-uuid params)
        result   (async/chan)]
    (enc/if-let [app       (get @apps* app-uuid)
                 remote    (get (remotes app) remote-name)
                 transmit! (-> remote :transmit!)
                 ast       (eql/query->ast (or query mutation))
                 tx-id     (random-uuid)]
      (do
        (send-started! app remote-name tx-id (or query mutation))
        (transmit! remote {:com.fulcrologic.fulcro.algorithms.tx-processing/id             tx-id
                           :com.fulcrologic.fulcro.algorithms.tx-processing/ast            ast
                           :com.fulcrologic.fulcro.algorithms.tx-processing/idx            0
                           :com.fulcrologic.fulcro.algorithms.tx-processing/options        {}
                           :com.fulcrologic.fulcro.algorithms.tx-processing/update-handler identity
                           :com.fulcrologic.fulcro.algorithms.tx-processing/result-handler (fn [{:keys [body] :as result}]
                                                                                             (let [error? (ah/app-algorithm app :remote-error?)]
                                                                                               (if (error? result)
                                                                                                 (send-failed! app remote-name tx-id result)
                                                                                                 (send-finished! app remote-name tx-id body)))
                                                                                             (async/go
                                                                                               (async/>! result body)))}))
      (async/go (async/close! result)))
    result))

(defresolver statechart-definition-resolver [env input]
  {::pc/output [{:statechart/definitions [:statechart/registry-key
                                          :statechart/chart]}]}
  (let [params   (:query-params env)
        app-uuid (app-uuid params)
        app      (get @apps* app-uuid)]
    (when app
      (let [runtime-env          (some-> (runtime-atom app) deref :com.fulcrologic.statecharts/env)
            chart-id->definition (some-> runtime-env :com.fulcrologic.statecharts/statechart-registry :charts deref
                                   strip-lambdas)
            definitions          (mapv (fn [[k v]]
                                         {:statechart/registry-key k
                                          :statechart/chart        v})
                                   chart-id->definition)]
        {:statechart/definitions definitions}))))

(defresolver statechart-session-resolver [env input]
  {::pc/output [{:statechart/available-sessions [:com.fulcrologic.statecharts/session-id
                                                 :com.fulcrologic.statecharts/history-value
                                                 :com.fulcrologic.statecharts/parent-session-id
                                                 :com.fulcrologic.statecharts/statechart-src
                                                 :com.fulcrologic.statecharts/configuration
                                                 {:com.fulcrologic.statecharts/statechart}]}]}
  (let [params   (:query-params env)
        app-uuid (app-uuid params)
        app      (get @apps* app-uuid)]
    (when app
      (let [{session-id->session :com.fulcrologic.statecharts/session-id :as state-map} (app-state app)
            runtime-env          (some-> (runtime-atom app) deref :com.fulcrologic.statecharts/env)
            chart-id->definition (some-> runtime-env :com.fulcrologic.statecharts/statechart-registry :charts deref
                                   strip-lambdas)
            available-sessions   (mapv
                                   (fn [session]
                                     (let [src-id (:com.fulcrologic.statecharts/statechart-src session)]
                                       (-> session
                                         (select-keys [:com.fulcrologic.statecharts/session-id
                                                       :com.fulcrologic.statecharts/history-value
                                                       :com.fulcrologic.statecharts/parent-session-id
                                                       :com.fulcrologic.statecharts/statechart-src
                                                       :com.fulcrologic.statecharts/configuration])
                                         (assoc :com.fulcrologic.statecharts/statechart {:statechart/registry-key src-id
                                                                                         :statechart/chart        (chart-id->definition src-id)}))))
                                   (vals session-id->session))]
        {:statechart/available-sessions available-sessions}))))
