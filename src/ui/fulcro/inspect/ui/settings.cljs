(ns fulcro.inspect.ui.settings
  (:require
    [fulcro.client.dom :as dom :refer [div button input a]]
    [fulcro.client.mutations :as m :refer [defmutation]]
    [fulcro.client.primitives :as fp :refer [defsc]]
    [fulcro.client.data-fetch :as df]
    [fulcro.inspect.helpers :as h]))

(def settings (atom {}))
(defn get-setting [k]
  (get @settings k))

(defmutation cache-settings [params]
  (action [{:keys [state ref]}]
    (swap! settings merge (get-in @state ref) params)))

(defmutation save-settings [params]
  (remote [env]
    (-> env :ast (assoc :key 'save-settings)))
  (action [env]
    (h/swap-entity! env merge params)
    (swap! settings merge params)))

(defn load-settings-mutation [ident query]
  (df/load-mutation
    {:target        ident
     :query         [{:>/SETTINGS (filterv #(= "setting" (namespace %)) query)}]
     :refresh       [::id]
     :post-mutation `cache-settings}))

(defsc Settings [this {:setting/keys [websocket-port compact-keywords?]} {:keys [close-settings!]}]
  {:query             [::id :setting/websocket-port :setting/compact-keywords?]
   :ident             (fn [] [::id :main])
   :componentDidMount (fn []
                        (fp/transact! this
                          (load-settings-mutation
                            (fp/get-ident this)
                            (fp/get-query this))))
   :initial-state     {::id                       :main
                       :setting/websocket-port    0
                       :setting/compact-keywords? true}}
  (div :.ui.container {:style {:margin "1rem"}}
    (when close-settings!
      (fp/fragment
        (button :.ui.button.labeled.icon.basic.negative
          {:onClick close-settings!}
          (dom/i :.icon.close)
          "Close Settings")
        (div :.ui.divider.horizontal.hidden)))
    (div :.ui.form.big
      (when (-> this fp/shared :fulcro.inspect.renderer/electron?)
        (div :.fields
          (div :.field.inline
            (dom/label "Websocket Port: ")
            (input {:value    (or websocket-port 0)
                    :type     "number"
                    :onChange #(m/set-integer! this :setting/websocket-port :event %)}))
          (button :.ui.button.primary.positive
            {:onClick #(fp/transact! this `[(save-settings {:setting/websocket-port ~websocket-port})])}
            (dom/span :.ui.text.large "Restart Websockets"))))
      (div :.field.inline
        (dom/label "Compact Keywords in DB Explorer?")
        (input :.ui.checkbox
          {:checked  (or compact-keywords? false)
           :type     "checkbox"
           :onChange #(fp/transact! this `[(save-settings {:setting/compact-keywords? ~(not compact-keywords?)})])})))))

(def ui-settings (fp/factory Settings))
