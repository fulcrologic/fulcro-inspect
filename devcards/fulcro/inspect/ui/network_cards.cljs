(ns fulcro.inspect.ui.network-cards
  (:require
    [devcards.core :refer-macros [defcard]]
    [fulcro-css.css :as css]
    [fulcro.client.cards :refer-macros [defcard-fulcro]]
    [fulcro.inspect.ui.network :as network]
    [fulcro.inspect.card-helpers :as card-helpers]
    [om.next :as om]))

(def req
  {})

(def NetworkRoot (card-helpers/make-root network/NetworkHistory ::single-tx))

(defcard-fulcro network
  NetworkRoot
  {}
  {:fulcro {:started-callback
            (fn [{:keys [reconciler]}]
              (let [ref (-> reconciler om/app-state deref :ui/root)]
                (doseq [x (repeat 5 req)]
                  (om/transact! reconciler ref [`(network/request-start ~x)]))))}})

(css/upsert-css "network" network/NetworkHistory)
