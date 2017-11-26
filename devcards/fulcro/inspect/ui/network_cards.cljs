(ns fulcro.inspect.ui.network-cards
  (:require
    [devcards.core :refer-macros [defcard]]
    [fulcro-css.css :as css]
    [fulcro.client.cards :refer-macros [defcard-fulcro]]
    [fulcro.inspect.ui.network :as network]
    [om.next :as om]
    [fulcro.client.core :as fulcro]
    [clojure.test.check.generators :as gen]
    [om.dom :as dom]))

(def request-samples
  [{:in  [:hello :world]
    :out {:hello "data"
          :world "value"}}
   `{:in  [(do-something {:foo "bar"})]
     :out {do-something {}}}
   {:in  [{:ui/root
           [{:ui/inspector
             [:fulcro.inspect.core/inspectors
              {:fulcro.inspect.core/current-app
               [:fulcro.inspect.ui.inspector/tab
                :fulcro.inspect.ui.inspector/id
                {:fulcro.inspect.ui.inspector/app-state
                 [:fulcro.inspect.ui.data-watcher/id
                  {:fulcro.inspect.ui.data-watcher/root-data
                   [:fulcro.inspect.ui.data-viewer/id
                    :fulcro.inspect.ui.data-viewer/content
                    :fulcro.inspect.ui.data-viewer/expanded]}
                  {:fulcro.inspect.ui.data-watcher/watches
                   [:ui/expanded?
                    :fulcro.inspect.ui.data-watcher/watch-id
                    :fulcro.inspect.ui.data-watcher/watch-path
                    {:fulcro.inspect.ui.data-watcher/data-viewer
                     [:fulcro.inspect.ui.data-viewer/id
                      :fulcro.inspect.ui.data-viewer/content
                      :fulcro.inspect.ui.data-viewer/expanded]}]}]}
                {:fulcro.inspect.ui.inspector/network
                 [:fulcro.inspect.ui.network/history-id
                  {:fulcro.inspect.ui.network/requests
                   [:fulcro.inspect.ui.network/request-id
                    :fulcro.inspect.ui.network/request-edn
                    :fulcro.inspect.ui.network/request-edn-row-view
                    :fulcro.inspect.ui.network/response-edn
                    :fulcro.inspect.ui.network/request-started-at
                    :fulcro.inspect.ui.network/request-finished-at
                    :fulcro.inspect.ui.network/error]}
                  {:fulcro.inspect.ui.network/active-request
                   [:fulcro.inspect.ui.network/request-id
                    :fulcro.inspect.ui.network/request-edn
                    :fulcro.inspect.ui.network/response-edn
                    :fulcro.inspect.ui.network/request-started-at
                    :fulcro.inspect.ui.network/request-finished-at
                    :fulcro.inspect.ui.network/error
                    {:ui/request-edn-view
                     [:fulcro.inspect.ui.data-viewer/id
                      :fulcro.inspect.ui.data-viewer/content
                      :fulcro.inspect.ui.data-viewer/expanded]}
                    {:ui/response-edn-view
                     [:fulcro.inspect.ui.data-viewer/id
                      :fulcro.inspect.ui.data-viewer/content
                      :fulcro.inspect.ui.data-viewer/expanded]}
                    {:ui/error-view
                     [:fulcro.inspect.ui.data-viewer/id
                      :fulcro.inspect.ui.data-viewer/content
                      :fulcro.inspect.ui.data-viewer/expanded]}]}]}
                {:fulcro.inspect.ui.inspector/transactions
                 [:fulcro.inspect.ui.transactions/tx-list-id
                  :fulcro.inspect.ui.transactions/tx-filter
                  {:fulcro.inspect.ui.transactions/active-tx
                   [:fulcro.inspect.ui.transactions/tx-id
                    :fulcro.inspect.ui.transactions/timestamp
                    :tx
                    :ret
                    :sends
                    :old-state
                    :new-state
                    :ref
                    :component
                    {:ui/tx-view
                     [:fulcro.inspect.ui.data-viewer/id
                      :fulcro.inspect.ui.data-viewer/content
                      :fulcro.inspect.ui.data-viewer/expanded]}
                    {:ui/ret-view
                     [:fulcro.inspect.ui.data-viewer/id
                      :fulcro.inspect.ui.data-viewer/content
                      :fulcro.inspect.ui.data-viewer/expanded]}
                    {:ui/tx-row-view
                     [:fulcro.inspect.ui.data-viewer/id
                      :fulcro.inspect.ui.data-viewer/content
                      :fulcro.inspect.ui.data-viewer/expanded]}
                    {:ui/sends-view
                     [:fulcro.inspect.ui.data-viewer/id
                      :fulcro.inspect.ui.data-viewer/content
                      :fulcro.inspect.ui.data-viewer/expanded]}
                    {:ui/old-state-view
                     [:fulcro.inspect.ui.data-viewer/id
                      :fulcro.inspect.ui.data-viewer/content
                      :fulcro.inspect.ui.data-viewer/expanded]}
                    {:ui/new-state-view
                     [:fulcro.inspect.ui.data-viewer/id
                      :fulcro.inspect.ui.data-viewer/content
                      :fulcro.inspect.ui.data-viewer/expanded]}
                    {:ui/diff-add-view
                     [:fulcro.inspect.ui.data-viewer/id
                      :fulcro.inspect.ui.data-viewer/content
                      :fulcro.inspect.ui.data-viewer/expanded]}
                    {:ui/diff-rem-view
                     [:fulcro.inspect.ui.data-viewer/id
                      :fulcro.inspect.ui.data-viewer/content
                      :fulcro.inspect.ui.data-viewer/expanded]}]}
                  {:fulcro.inspect.ui.transactions/tx-list
                   [:fulcro.inspect.ui.transactions/tx-id
                    :fulcro.inspect.ui.transactions/timestamp
                    :ref
                    :tx
                    {:ui/tx-row-view
                     [:fulcro.inspect.ui.data-viewer/id
                      :fulcro.inspect.ui.data-viewer/content
                      :fulcro.inspect.ui.data-viewer/expanded]}]}]}]}]}
            :ui/size
            :ui/visible?]}
          :ui/react-key]
    :out {}}])

(defn gen-remote []
  (gen/generate (gen/frequency [[6 (gen/return :remote)] [1 (gen/return :other)]])))

(defn success? []
  (gen/generate (gen/frequency [[8 (gen/return true)] [1 (gen/return false)]])))

(defn gen-request [this]
  (let [id         (random-uuid)
        reconciler (om/get-reconciler this)
        remote     (gen-remote)
        {:keys [in out]} (rand-nth request-samples)]
    (om/transact! reconciler [::network/history-id "main"]
      [`(network/request-start ~{::network/remote      remote
                                 ::network/request-id  id
                                 ::network/request-edn in})])
    (js/setTimeout
      (fn []
        (let [suc? (success?)]
          (om/transact! reconciler [::network/history-id "main"]
            [`(network/request-finish ~(cond-> {::network/remote     remote
                                                ::network/request-id id}
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
  {})

(css/upsert-css "network" NetworkRoot)
