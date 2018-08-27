(ns fulcro.inspect.ui.data-history-spec
  (:require [fulcro-spec.core :refer [specification behavior component assertions]]
            [fulcro.inspect.ui.data-history :as data-history]
            [fulcro.client.primitives :as fp]
            [fulcro.inspect.helpers :as h]
            [fulcro.inspect.card-helpers :as ch]
            [nubank.workspaces.core :refer [deftest]]))

(def Root (ch/make-root data-history/DataHistory nil))

(deftest test-set-content
  (with-redefs [data-history/new-state identity]
    (behavior "history is not full"
      (behavior "Add item while is in the end of history"
        (let [state (atom
                      (-> (fp/get-initial-state Root {:foo "initial"})
                          (assoc-in [:ui/root ::data-history/history-id] "test")
                          (as-> <> (fp/tree->db Root <> true))))]
          (data-history/set-content {:ref   [::data-history/history-id "test"]
                                     :state state}
            {:foo "new data"})
          (assertions
            "New entry is appended to the end"
            (get-in @state [::data-history/history-id "test" ::data-history/history])
            => [{:foo "initial"}
                {:foo "new data"}]

            "Current index is updated"
            (get-in @state [::data-history/history-id "test" ::data-history/current-index])
            => 1

            "Watcher content is updated"
            (get-in @state (h/resolve-path @state
                             [::data-history/history-id "test" ::data-history/watcher
                              :fulcro.inspect.ui.data-watcher/root-data
                              :fulcro.inspect.ui.data-viewer/content]))
            => {:foo "new data"})))

      (behavior "Add item while looking at past"
        (let [state (atom
                      (-> (fp/get-initial-state Root {:foo "initial"})
                          (update :ui/root assoc
                            ::data-history/history-id "test"
                            ::data-history/history [{:foo "initial"}
                                                    {:foo "new data"}]
                            ::data-history/current-index 0)
                          (as-> <> (fp/tree->db Root <> true))))]
          (data-history/set-content {:ref   [::data-history/history-id "test"]
                                     :state state}
            {:foo "third item!"})
          (assertions
            "New entry is appended to the end"
            (get-in @state [::data-history/history-id "test" ::data-history/history])
            => [{:foo "initial"}
                {:foo "new data"}
                {:foo "third item!"}]

            "Current index is kept"
            (get-in @state [::data-history/history-id "test" ::data-history/current-index])
            => 0

            "Watcher content is not updated"
            (get-in @state (h/resolve-path @state
                             [::data-history/history-id "test" ::data-history/watcher
                              :fulcro.inspect.ui.data-watcher/root-data
                              :fulcro.inspect.ui.data-viewer/content]))
            => {:foo "initial"}))))

    (binding [data-history/*max-history* 3]
      (behavior "history is full"
        (behavior "Add item while is in the end of history"
          (let [state (atom
                        (-> (fp/get-initial-state Root {:foo "initial"})
                            (update :ui/root assoc
                              ::data-history/history-id "test"
                              ::data-history/history [{:foo "initial"}
                                                      {:foo "new data"}
                                                      {:foo "third item!"}]
                              ::data-history/current-index 2)
                            (as-> <> (fp/tree->db Root <> true))))]
            (data-history/set-content {:ref   [::data-history/history-id "test"]
                                       :state state}
              {:foo "blow up"})
            (assertions
              "New entry is appended to the end"
              (get-in @state [::data-history/history-id "test" ::data-history/history])
              => [{:foo "new data"}
                  {:foo "third item!"}
                  {:foo "blow up"}]

              "Current index is kept at end"
              (get-in @state [::data-history/history-id "test" ::data-history/current-index])
              => 2

              "Watcher content is updated"
              (get-in @state (h/resolve-path @state
                               [::data-history/history-id "test" ::data-history/watcher
                                :fulcro.inspect.ui.data-watcher/root-data
                                :fulcro.inspect.ui.data-viewer/content]))
              => {:foo "blow up"})))

        (behavior "Add item while looking at past"
          (let [state (atom
                        (-> (fp/get-initial-state Root {:foo "initial"})
                            (update :ui/root assoc
                              ::data-history/history-id "test"
                              ::data-history/history [{:foo "initial"}
                                                      {:foo "new data"}
                                                      {:foo "third item!"}]
                              ::data-history/current-index 1)
                            (as-> <> (fp/tree->db Root <> true))))]
            (data-history/set-content {:ref   [::data-history/history-id "test"]
                                       :state state}
              {:foo "blow up"})
            (assertions
              "New entry is appended to the end"
              (get-in @state [::data-history/history-id "test" ::data-history/history])
              => [{:foo "new data"}
                  {:foo "third item!"}
                  {:foo "blow up"}]

              "Current index goes back"
              (get-in @state [::data-history/history-id "test" ::data-history/current-index])
              => 0

              "Watcher content is not updated"
              (get-in @state (h/resolve-path @state
                               [::data-history/history-id "test" ::data-history/watcher
                                :fulcro.inspect.ui.data-watcher/root-data
                                :fulcro.inspect.ui.data-viewer/content]))
              => {:foo "initial"})))))))
