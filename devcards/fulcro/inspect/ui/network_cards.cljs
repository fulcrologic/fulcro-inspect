(ns fulcro.inspect.ui.network-cards
  (:require
    [devcards.core :refer-macros [defcard]]
    [fulcro-css.css :as css]
    [fulcro.client.cards :refer-macros [defcard-fulcro]]
    [fulcro.inspect.ui.network :as network]
    [fulcro.inspect.card-helpers :as card-helpers]
    [om.next :as om]
    [fulcro.client.core :as fulcro]
    [om.dom :as dom]))

(om/defui ^:once NetworkRoot
  static fulcro/InitialAppState
  (initial-state [_ _] {:ui/react-key (random-uuid)
                        :ui/root      (fulcro/get-initial-state network/NetworkHistory {})})

  static om/IQuery
  (query [_] [{:ui/root (om/get-query network/NetworkHistory)}
              :ui/react-key])

  static css/CSS
  (local-rules [_] [[:.container {:height         "300px"
                                  :display        "flex"
                                  :flex-direction "column"}]])
  (include-children [_] [network/NetworkHistory])

  Object
  (render [this]
    (let [{:keys [ui/react-key ui/root]} (om/props this)
          css (css/get-classnames NetworkRoot)]
      (dom/div #js {:key react-key :className (:container css)}
        (network/network-history root)))))

(defcard-fulcro network
  NetworkRoot
  {}
  {:fulcro {:started-callback
            (fn [{:keys [reconciler]}]
              (let [ref (-> reconciler om/app-state deref :ui/root)]
                (dotimes [i 10]
                  (doseq [tx [(list `network/request-start {::network/request-id  (str "query" i)
                                                            ::network/request-edn [:hello :world]})
                              (list `network/request-start {::network/request-edn [:pending :query]})
                              (list `network/request-update {::network/request-id   (str "query" i)
                                                             ::network/response-edn {:hello "data"
                                                                                     :world "value"}})
                              (list `network/request-start {::network/request-id  (str "mutation" i)
                                                            ::network/request-edn `[(do-something {:foo "bar"})]})
                              (list `network/request-update {::network/request-id   (str "mutation" i)
                                                             ::network/response-edn `{do-something {}}})]]
                    (om/transact! reconciler ref [tx])))))}})

(css/upsert-css "network" NetworkRoot)
