(ns fulcro.inspect.ui.network-cards
  (:require
    [devcards.core :refer-macros [defcard]]
    [fulcro-css.css :as css]
    [fulcro.client.cards :refer-macros [defcard-fulcro]]
    [fulcro.inspect.ui.network :as network]
    [fulcro.client.primitives :as fp]
    [fulcro.client.network :as f.network]
    [fulcro.client.data-fetch :as fetch]
    [fulcro.client.mutations :as mutations]
    [clojure.test.check.generators :as gen]
    [fulcro.client.dom :as dom]
    [cljs.spec.alpha :as s]))

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
        reconciler (fp/get-reconciler this)
        remote     (gen-remote)
        {:keys [in out]} (rand-nth request-samples)]
    (fp/transact! reconciler [::network/history-id "main"]
      [`(network/request-start ~{::network/remote      remote
                                 ::network/request-id  id
                                 ::network/request-edn in})])
    (js/setTimeout
      (fn []
        (let [suc? (success?)]
          (fp/transact! reconciler [::network/history-id "main"]
            [`(network/request-finish ~(cond-> {::network/remote     remote
                                                ::network/request-id id}
                                         suc? (assoc ::network/response-edn out)
                                         (not suc?) (assoc ::network/error {:error "bad"})))])))
      (gen/generate (gen/large-integer* {:min 30 :max 7000})))))

(fp/defui ^:once NetworkRoot
  static fp/InitialAppState
  (initial-state [_ _] {:ui/react-key (random-uuid)
                        :ui/root      (assoc (fp/get-initial-state network/NetworkHistory {})
                                        ::network/history-id "main")})

  static fp/IQuery
  (query [_] [{:ui/root (fp/get-query network/NetworkHistory)}
              :ui/react-key])

  static css/CSS
  (local-rules [_] [[:.container {:height         "500px"
                                  :display        "flex"
                                  :flex-direction "column"}]])
  (include-children [_] [network/NetworkHistory])

  Object
  (render [this]
    (let [{:keys [ui/react-key ui/root]} (fp/props this)
          css (css/get-classnames NetworkRoot)]
      (dom/div #js {:key react-key :className (:container css)}
        (dom/button #js {:onClick #(gen-request this)}
          "Generate request")
        (network/network-history root)))))

(defcard-fulcro network
  NetworkRoot
  {})

(s/def ::name #{"Arnold" "Bea" "Dude" "Girl"})

(mutations/defmutation send-something [_]
  (action [_] (js/console.log "send something"))
  (remote [_] true))

(fp/defsc NameLoader
  [this {::keys [name]} computed]
  {:initial-state {::id "name-loader"}
   :ident         [::id ::id]
   :query         [::id ::name]}
  (let [css (css/get-classnames NameLoader)]
    (dom/div nil
      (dom/button #js {:onClick #(fetch/load-field this ::name)}
        "Load name")
      (if name
        (str "The name is: " name))
      (dom/div nil
        (dom/button #js {:onClick #(fp/transact! this [`(send-something {})])}
          "Send")))))

(def name-loader (fp/factory NameLoader))

(fp/defui ^:once NameLoaderRoot
  static fp/InitialAppState
  (initial-state [_ _] {:ui/react-key (random-uuid)
                        :ui/root      (fp/get-initial-state NameLoader {})})

  static fp/IQuery
  (query [_] [{:ui/root (fp/get-query NameLoader)}
              :ui/react-key])

  static css/CSS
  (local-rules [_] [])
  (include-children [_] [NameLoader])

  Object
  (render [this]
    (let [{:keys [ui/react-key ui/root]} (fp/props this)]
      (dom/div #js {:key react-key}
        (name-loader root)))))

(defcard-fulcro network-sampler
  NameLoaderRoot
  {}
  {:fulcro {:networking
            (reify
              f.network/FulcroNetwork
              (send [this edn ok error]
                (ok {[::id "name-loader"] {::name (gen/generate (s/gen ::name))}}))
              (start [_]))}})

(defcard-fulcro network-sampler-remote-i
  NameLoaderRoot
  {}
  {:fulcro {:networking
            (reify
              f.network/FulcroRemoteI
              (transmit [this {::f.network/keys [edn ok-handler]}]
                (ok-handler {:transaction edn
                             :body {[::id "name-loader"] {::name (gen/generate (s/gen ::name))}}}))
              (abort [_ _]))}})

(css/upsert-css "network" NetworkRoot)
