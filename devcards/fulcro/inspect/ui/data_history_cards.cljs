(ns fulcro.inspect.ui.data-history-cards
  (:require
    [devcards.core :refer-macros [defcard]]
    [fulcro-css.css :as css]
    [fulcro.client.cards :refer-macros [defcard-fulcro]]
    [fulcro.inspect.ui.data-watcher :as watcher]
    [fulcro.inspect.ui.data-history :as data-history]
    [fulcro.inspect.card-helpers :as card-helpers]
    [fulcro.client.dom :as dom]
    [fulcro.client.primitives :as fp]))

(defonce n (atom 34))

(fp/defui ^:once Root
  static fp/InitialAppState
  (initial-state [_ state] {:ui/react-key (random-uuid)
                            :ui/root      (-> (fp/get-initial-state data-history/DataHistory state)
                                              (assoc ::data-history/history-id "main"))})

  static fp/IQuery
  (query [_] [{:ui/root (fp/get-query data-history/DataHistory)}
              :ui/react-key])

  static css/CSS
  (local-rules [_] [])
  (include-children [_] [data-history/DataHistory])

  Object
  (render [this]
    (let [{:keys [ui/react-key ui/root]} (fp/props this)]
      (dom/div #js {:key react-key}
        (dom/button #js {:onClick #(fp/transact! (fp/get-reconciler this) [::data-history/history-id "main"]
                                     [`(data-history/set-content ~{:some    "data"
                                                                   :integer (swap! n inc)})])}
          "Increment")
        (data-history/data-history root)))))

(defcard-fulcro data-history
  Root
  (card-helpers/init-state-atom Root
    {:some    "data"
     :integer 34}))

(css/upsert-css "data-watcher" data-history/DataHistory)
