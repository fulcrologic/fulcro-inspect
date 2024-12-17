(ns fulcro.inspect.lib.history
  (:require
    [com.fulcrologic.devtools.devtool-io :as devt]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.algorithms.normalized-state :as fns]
    [com.fulcrologic.fulcro.mutations :as fm]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp]
    [fulcro.inspect.helpers :as h]
    [fulcro.inspect.lib.diff :as diff]
    [taoensso.encore :as enc]
    [taoensso.timbre :as log]))

(def DB_HISTORY_BUFFER_SIZE 200)

(defn history-step-ident [app-uuid version]
  [:history/id [app-uuid version]])

(comp/defsc HistoryStep [_ {::app/keys    [id]
                            :history/keys [version]}]
  {:query [::app/id
           :history/based-on                                ; if there is a diff, this is the version that it is based on
           :history/diff                                    ; the diff from based-on to version
           :history/version
           :history/value]
   :ident (fn [] (history-step-ident id version))})

(defn latest-state-version
  "The newest version of history known for the currently selected target app"
  ([this]
   (let [state    (app/current-state this)
         app-uuid (h/current-app-uuid state)]
     (latest-state-version this app-uuid)))
  ([this app-uuid]
   (let [state   (app/current-state this)
         history (get state :history/id)]
     (reduce (fn [acc [id version]]
               (if (= id app-uuid)
                 (max acc version)
                 acc))
       0 (keys history)))))

(defn prune-history*
  "Mutation helper. Removes old entries from the history."
  [state-map]
  (let [history               (get state-map :history/id)
        all-keys              (sort (keys history))
        keys-by-app           (group-by first all-keys)
        truncated-keys-by-app (enc/map-vals (fn [v]
                                              (let [n (count v)]
                                                (if (> n DB_HISTORY_BUFFER_SIZE)
                                                  (into [] (take (- n DB_HISTORY_BUFFER_SIZE)) v)
                                                  [])))
                                keys-by-app)
        idents-to-drop        (mapv #(vector :history/id %) (apply concat (vals truncated-keys-by-app)))]
    (reduce fns/remove-entity state-map idents-to-drop)))

(defn version-of-state-map
  "Get the value of a state in history that has the given state id. Returns an empty map
   if the state is not currently available."
  [app-ish app-uuid version]
  (try
    (if (int? version)
      (let [state-map (app/current-state app-ish)]
        (get-in state-map [:history/id [app-uuid version] :history/value] {}))
      {})
    (catch :default _
      {})))

(defn history-step
  "Return a history step (map w/id and value) for the given state id."
  [app-ish app-uuid version]
  (get-in (app/current-state app-ish) (history-step-ident app-uuid version)))

;; FIXME
(fm/defmutation clear-history [{::app/keys [id]}]
  (action [{:keys [state]}]
    (swap! state (fn [s] (enc/remove-keys (fn [[app-uuid _]] (= app-uuid id)) s)))))

(defn closest-populated-history-step
  "Find a history step that has a populated value, "
  ([this min-version]
   (let [state    (app/current-state this)
         app-uuid (h/current-app-uuid state)]
     (closest-populated-history-step this app-uuid min-version)))
  ([this app-uuid min-version]
   (let [state        (app/current-state this)
         best-version (reduce-kv
                        (fn [found-version [id version] {:history/keys [value]}]
                          (if (and (= id app-uuid) value (>= version found-version))
                            version
                            found-version))
                        0
                        (get state :history/id))]
     (history-step this app-uuid best-version))))

(fm/defmutation apply-diff [{::app/keys    [id]
                             :history/keys [version]}]
  (action [{:keys [state]}]
    (let [target-ident [:history/id [id version]]
          {:history/keys [based-on diff] :as target} (get-in @state target-ident)
          {:history/keys [value]} (get-in @state [:history/id [id based-on]])
          new-entry    (assoc target :history/value (diff/patch value diff))]
      (swap! state assoc-in target-ident new-entry))))

(defn fetch-history-step!
  "Fetch a history step for the currently-selected app"
  ([this version]
   (let [app-uuid (h/current-app-uuid (app/current-state this))] (fetch-history-step! this app-uuid version)))
  ([this app-uuid version-to-load]
   (let [{:history/keys [version value]} (closest-populated-history-step this app-uuid version-to-load)]
     (when (empty? value)
       (devt/load! this app-uuid [:history/id [app-uuid version-to-load]] HistoryStep
         {:post-mutation        `apply-diff
          :params               {:based-on version}
          :post-mutation-params {::app/id         app-uuid
                                 :history/version version-to-load}})))))

(defn auto-advance-history*
  "Move data history so that it is on the most recent version IF the user does not have the slider positioned."
  [state-map app-id]
  (let [ident     [:data-history/id [:x app-id]]
        {:data-history/keys [history current-index]} (get-in state-map ident)
        nsteps    (count history)
        new-index (if (or (>= current-index nsteps) (= (- nsteps 2) current-index))
                    (dec nsteps)
                    current-index)]
    (assoc-in state-map (conj ident :data-history/current-index) new-index)))

(defn save-history-step* [state-map step]
  (let [{::app/keys    [id]
         :history/keys [based-on diff value]} step
        base-value (:history/value (get-in state-map [:history/id [id based-on]]))
        value      (cond
                     value value
                     (and base-value based-on diff) (diff/patch base-value diff))
        step       (if value (-> step
                               ;                 (dissoc :history/diff :history/based-on)
                               (assoc :history/value value)) step)]
    (-> state-map
      (prune-history*)
      (merge/merge-component HistoryStep (log/spy :info step)
        :append [:data-history/id [:x id] :data-history/history])
      (auto-advance-history* id)
      )))

(fm/defmutation save-history-step
  "Mutation: Save a history step."
  [step]
  (action [{:keys [app state]}]
    (swap! state save-history-step* step)))
