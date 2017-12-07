(ns fulcro.inspect.ui.data-history
  (:require [fulcro.client.primitives :as fp]
            [fulcro-css.css :as css]
            [fulcro.client.dom :as dom]
            [fulcro.client.mutations :as mutations]
            [fulcro.inspect.ui.data-watcher :as watcher]
            [fulcro.inspect.ui.core :as ui]
            [fulcro.inspect.helpers :as h]))

(def ^:dynamic *max-history* 100)

(defn new-state [content]
  {::state     content
   ::timestamp (js/Date.)})

(mutations/defmutation ^:intern set-content [content]
  (action [env]
    (let [{:keys [state ref]} env
          {::keys [watcher current-index history]} (get-in @state ref)]
      (if (or (= 0 (count history))
              (= current-index (dec (count history))))
        (do
          (if-not (= current-index (dec *max-history*))
            (h/swap-entity! env update ::current-index inc))
          (watcher/update-state (assoc env :ref watcher) content))

        (if (= *max-history* (count history))
          (h/swap-entity! env update ::current-index dec)))

      (h/swap-entity! env update ::history #(->> (conj % (new-state content))
                                                 (take-last *max-history*)
                                                 (vec))))))

(mutations/defmutation ^:intern navigate-history [{::keys [current-index]}]
  (action [env]
    (let [{:keys [state ref]} env
          history (get-in @state ref)]
      (when (not= current-index (::current-index history))
        (let [content (get-in history [::history current-index ::state])]
          (h/swap-entity! env assoc ::current-index current-index)
          (watcher/update-state (assoc env :ref (::watcher history)) content))))))

(fp/defsc DataHistory
  [this {::keys [history watcher current-index]} computed]
  {:initial-state (fn [content]
                    {::history-id    (random-uuid)
                     ::history       [(new-state content)]
                     ::current-index 0
                     ::watcher       (fp/get-initial-state watcher/DataWatcher content)})
   :ident         [::history-id ::history-id]
   :query         [::history-id ::history ::current-index
                   {::watcher (fp/get-query watcher/DataWatcher)}]
   :css           [[:.container {:width "100%"
                                 :flex  "1"}]
                   [:.slider {:display "flex"}]]
   :css-include   [ui/CSS watcher/DataWatcher]}
  (let [css (css/get-classnames DataHistory)]
    (dom/div #js {:className (:container css)}
      (dom/div #js {:className (:slider css)}
        (dom/button #js {:onClick  #(fp/transact! this [`(navigate-history ~{::current-index (dec current-index)})])
                         :disabled (= 0 current-index)}
          "<")
        (dom/div nil (dom/input #js {:type     "range" :min "0" :max (dec (count history))
                                     :value    (str current-index)
                                     :onChange #(fp/transact! this [`(navigate-history {::current-index ~(js/parseInt (.. % -target -value))})])}))
        (dom/button #js {:onClick  #(fp/transact! this [`(navigate-history ~{::current-index (inc current-index)})])
                         :disabled (= (dec (count history)) current-index)}
          ">"))
      (watcher/data-watcher watcher))))

(def data-history (fp/factory DataHistory))
