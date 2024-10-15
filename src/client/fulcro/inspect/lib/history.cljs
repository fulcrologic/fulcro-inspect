(ns fulcro.inspect.lib.history
  (:require
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.fulcro.components]
    [com.fulcrologic.fulcro.mutations :as fm]
    [fulcro.inspect.helpers :as h]))

(def DB_HISTORY_BUFFER_SIZE 80)
(def DB_HISTORY_BUFFER_WINDOW 20)

(defn history-by-state-id
  "Find the state database history for the given app-uuid, given a component or
  app."
  [app-ish app-uuid]
  (let [{::keys [db-hash-index]} (comp/shared app-ish)]
    (some-> db-hash-index deref (get app-uuid))))

(defn latest-state-id [app-ish app-uuid]
  (-> (history-by-state-id app-ish app-uuid) keys last))

(defn db-index-add
  [db app-uuid {:keys [id value] :as history-step}]
  (if id
    (let [history        (get db app-uuid (sorted-map))
          pruned-history (if (> (count history) DB_HISTORY_BUFFER_SIZE)
                           (into (sorted-map)
                             (drop DB_HISTORY_BUFFER_WINDOW history))
                           history)
          new-history    (assoc pruned-history id value)]
      (assoc db app-uuid new-history))
    db))

(defn state-map-for-id
  "Get the value of a state in history that has the given state id. Returns an empty map
   with the entry `:inspect/fetched? false` if the state is not currently available."
  [app-ish app-uuid state-id]
  (try
    (if (int? state-id)
      (let [history (history-by-state-id app-ish app-uuid)]
        (get history state-id))
      {})
    (catch :default _
      {})))

(defn history-step
  "Return a history step (map w/id and value) for the given state id."
  [app-ish app-uuid state-id]
  (let [state-map (state-map-for-id app-ish app-uuid state-id)]
    {:id    state-id
     :value state-map}))

(defn record-history-step!
  "Record a history step. History steps are `{:id n :value state-map}`. A history step
   can be recorded without a value, in which case the user will be able to request it
   on demand when needed."
  [app-ish app-uuid history-step]
  (let [{::keys [db-hash-index] :as shared} (comp/shared app-ish)]
    (swap! db-hash-index db-index-add app-uuid history-step)))

(defn clear-history! [app-ish app-uuid]
  (let [{::keys [db-hash-index]} (comp/shared app-ish)]
    (swap! db-hash-index dissoc app-uuid)))

(defn- best-populated-base
  "Given the actual map from state IDs to values: Find the one closest in time to the given ID that is before it
  and return that ID."
  [history id]
  (let [fetched-ids (remove (fn [entry-id] (empty? (get history entry-id))) (sort (keys history)))
        best-id     (last (take-while #(<= % id) fetched-ids))]
    best-id))

(defn closest-populated-history-step
  [this id]
  (let [state    (app/current-state this)
        app-uuid (h/current-app-uuid @state)
        history  (when app-uuid (history-by-state-id this app-uuid))
        base     (when history (best-populated-base history id))
        value    (state-map-for-id this app-uuid base)]
    (when value
      {:id    base
       :value value})))

(fm/defmutation remote-fetch-history-step [{:keys [id]}]
  (refresh [_env] [:fulcro.inspect.ui.data-viewer/content])
  (remote [{:keys [state app] :as env}]
    (let [app-uuid (h/current-app-uuid @state)
          history  (when app-uuid (history-by-state-id app app-uuid))
          base     (when history (best-populated-base history id))
          fake-ref [:ignored [:ignored app-uuid]]
          env      (cond-> (assoc env :ref fake-ref)
                     base (assoc-in [:ast :params :based-on] base))]
      (when (not= base id)
        (h/remote-mutation env 'fetch-history-step)))))
