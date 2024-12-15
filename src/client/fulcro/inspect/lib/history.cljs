(ns fulcro.inspect.lib.history
  (:require
    [com.fulcrologic.devtools.devtool-io :as devt]
    [com.fulcrologic.fulcro.mutations :as fm]
    [com.fulcrologic.fulcro.raw.application :as rapp]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.raw.components :as comp]
    [fulcro.inspect.helpers :as h]
    [fulcro.inspect.lib.diff :as diff]
    [taoensso.encore :as enc]))

(def DB_HISTORY_BUFFER_SIZE 80)
(def DB_HISTORY_BUFFER_WINDOW 20)

(comp/defnc HistoryStep
  [::app/id
   :history/based-on                                ; if there is a diff, this is the version that it is based on
   :history/diff                                    ; the diff from based-on to version
   :history/version
   :history/value]
  {:ident (fn [_ {::app/keys    [id]
                  :history/keys [version]}] [:history/id [id version]]) })

;; This query is used to fetch a diff, and a post-mutation to the load should be used to fix it to a HistoryStep
(comp/defnc HistoryDiff
  [::app/id
   :history/based-on                                ; if there is a diff, this is the version that it is based on
   :history/diff                                    ; the diff from based-on to version
   :history/version]
  {:ident (fn [_ {::app/keys    [id]
                  :history/keys [version]}] [:history/id [id version]]) })

(defn latest-state-version
  "The newest version of history known for the currently selected target app"
  ([this]
   (let [state    (rapp/current-state this)
         app-uuid (h/current-app-uuid state)]
     (latest-state-version this app-uuid)))
  ([this app-uuid]
   (let [state   (rapp/current-state this)
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
                                                  (into [] (drop (- n DB_HISTORY_BUFFER_SIZE)) v)
                                                  (vec v))))
                                keys-by-app)
        new-keys              (flatten (vals truncated-keys-by-app))
        new-history           (select-keys history new-keys)]
    (assoc state-map :history/id new-history)))

(defn version-of-state-map
  "Get the value of a state in history that has the given state id. Returns an empty map
   if the state is not currently available."
  [app-ish app-uuid version]
  (try
    (if (int? version)
      (let [state-map (rapp/current-state app-ish)]
        (get-in state-map [:history/id [app-uuid version] :history/value] {}))
      {})
    (catch :default _
      {})))

(defn history-step
  "Return a history step (map w/id and value) for the given state id."
  [app-ish app-uuid version]
  (get-in (rapp/current-state app-ish) [:history/id [app-uuid version]]))

(fm/defmutation clear-history [{::app/keys [id]}]
  (action [{:keys [state]}]
    (swap! state (fn [s] (enc/remove-keys (fn [[app-uuid _]] (= app-uuid id)) s)))))

(defn closest-populated-history-step
  "Find a history step that has a populated value, "
  ([this min-version]
   (let [state    (rapp/current-state this)
         app-uuid (h/current-app-uuid state)]
     (closest-populated-history-step this app-uuid min-version)))
  ([this app-uuid min-version]
   (let [state        (rapp/current-state this)
         best-version (reduce-kv
                        (fn [found-version [id version] {:history/keys [value]}]
                          (if (and (= id app-uuid) value (>= version found-version))
                            version
                            found-version))
                        0
                        (get state :history/id))]
     (history-step this app-uuid best-version))))

(fm/defmutation apply-diff [{::app/keys [id]
                             :keys      [target]}]
  (action [{:keys [state]}]
    (let [target-ident [:history/id [id target]]
          {:history/keys [based-on diff] :as target} (get-in @state target-ident)
          {:history/keys [value]} (get-in @state [:history/id [id based-on]])
          new-entry    (assoc target :history/value (diff/patch value diff))]
      (swap! state assoc-in target-ident new-entry))))

(defn fetch-history-step!
  "Fetch a history step for the currently-selected app"
  ([this version]
   (let [app-uuid (h/current-app-uuid (rapp/current-state this))] (fetch-history-step! this app-uuid version)))
  ([this app-uuid version-to-load]
   (let [{:history/keys [version value]} (closest-populated-history-step this app-uuid version-to-load)]
     (when-not (empty? value)
       (devt/load! this app-uuid :history/diff HistoryDiff
         {:post-mutation        apply-diff
          :target               [:history/id [app-uuid version-to-load]]
          :params               {::app/id  app-uuid
                                 :based-on version
                                 :target   version-to-load}
          :post-mutation-params {::app/id         app-uuid
                                 :history/version version-to-load}})))))
