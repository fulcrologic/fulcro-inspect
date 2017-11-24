(ns fulcro.inspect.ui.network-cards
  (:require
    [devcards.core :refer-macros [defcard]]
    [fulcro-css.css :as css]
    [fulcro.client.cards :refer-macros [defcard-fulcro]]
    [fulcro.inspect.ui.network :as network]
    [fulcro.inspect.card-helpers :as card-helpers]
    [om.next :as om]
    [fulcro.client.core :as fulcro]
    [clojure.test.check.generators :as gen]
    [om.dom :as dom]))

(def request-samples
  `[{:in  [:hello :world]
     :out {:hello "data"
           :world "value"}}
    {:in  [(do-something {:foo "bar"})]
     :out {do-something {}}}])

(defn success? []
  (gen/generate (gen/frequency [[8 (gen/return true)] [1 (gen/return false)]])))

(defn gen-request [this]
  (let [id         (random-uuid)
        reconciler (om/get-reconciler this)
        {:keys [in out]} (rand-nth request-samples)]
    (om/transact! reconciler [::network/history-id "main"]
      [`(network/request-start ~{::network/request-id id ::network/request-edn in})])
    (js/setTimeout
      (fn []
        (let [suc? (success?)]
          (om/transact! reconciler [::network/history-id "main"]
            [`(network/request-update ~(cond-> {::network/request-id id}
                                         suc? (assoc ::network/response-edn out)
                                         (not suc?) (assoc ::network/error {:error "bad"})))])))
      (gen/generate (gen/large-integer* {:min 30 :max 7000})))))

(om/defui ^:once NetworkRoot
  static fulcro/InitialAppState
  (initial-state [_ _] {:ui/react-key (random-uuid)
                        :ui/root      (assoc (fulcro/get-initial-state network/NetworkHistory {})
                                        ::network/history-id "main")})

  static om/IQuery
  (query [_] [{:ui/root (om/get-query network/NetworkHistory)}
              :ui/react-key])

  static css/CSS
  (local-rules [_] [[:.container {:height         "500px"
                                  :display        "flex"
                                  :flex-direction "column"}]])
  (include-children [_] [network/NetworkHistory])

  Object
  (render [this]
    (let [{:keys [ui/react-key ui/root]} (om/props this)
          css (css/get-classnames NetworkRoot)]
      (dom/div #js {:key react-key :className (:container css)}
        (dom/button #js {:onClick #(gen-request this)}
          "Generate request")
        (network/network-history root)))))

(defcard-fulcro network
  NetworkRoot
  {}
  {:fulcro {:started-callback
            (fn [{:keys [reconciler]}]
              (let [ref (-> reconciler om/app-state deref :ui/root)]
                #_(dotimes [i 10]
                    (doseq [tx `[(network/request-start {::network/request-id  ~(str "query" i)
                                                         ::network/request-edn [:hello :world]})
                                 (network/request-start {::network/request-edn [:pending :query]})
                                 (network/request-update {::network/request-id   ~(str "query" i)
                                                          ::network/response-edn {:hello "data"
                                                                                  :world "value"}})
                                 (network/request-start {::network/request-id  ~(str "mutation" i)
                                                         ::network/request-edn [(do-something {:foo "bar"})]})
                                 (network/request-update {::network/request-id   ~(str "mutation" i)
                                                          ::network/response-edn {do-something {}}})]]
                      (om/transact! reconciler ref [tx])))))}})

(css/upsert-css "network" NetworkRoot)
