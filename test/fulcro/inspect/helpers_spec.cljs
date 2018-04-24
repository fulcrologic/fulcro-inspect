(ns fulcro.inspect.helpers-spec
  (:require
    [fulcro-spec.core :refer [specification behavior component assertions]]
    [fulcro.inspect.helpers :as h]
    [fulcro.client.primitives :as fp]))

(fp/defui ^:once Child
  static fp/Ident
  (ident [_ props] [:child/id (:child/id props)])

  static fp/IQuery
  (query [_] [:child/id]))

(fp/defui ^:once Container
  static fp/InitialAppState
  (initial-state [_ x] (merge {:state :inited} x))

  static fp/Ident
  (ident [_ props] [:container/id (:container/id props)])

  static fp/IQuery
  (query [_] [:container/id
              {:child (fp/get-query Child)}]))

(specification "resolve-path"
  (assertions
    (h/resolve-path {:id {123 {:other [:id 456]}
                          456 {:a "x"}}}
      [:id])
    => [:id]
    (h/resolve-path {:id {123 {:other [:id 456]}
                          456 {:a "x"}}}
      [:id 123])
    => [:id 123]
    (h/resolve-path {:id {123 {:other [:id 456]}
                          456 {:a "x"}}}
      [:id 123 :other])
    => [:id 456]
    (h/resolve-path {:id {123 {:other [:id 456]}
                          456 {:a "x"
                               :b [:id2 333]}}}
      [:id 123 :other :b :done])
    => [:id2 333 :done]))

(specification "merge-entity"
  (assertions
    (h/merge-entity {} Container {:container/id "cont"
                                  :child        {:child/id "child"}})
    => {:container/id {"cont" {:container/id "cont"
                               :child        [:child/id "child"]}}
        :child/id     {"child" {:child/id "child"}}}

    (h/merge-entity {:container/id {"cont" {:container/id "cont"
                                            :child        [:child/id "child"]}}
                     :child/id     {"child" {:child/id   "child"
                                             :child/name "Bla"}}} Container
      {:container/id   "cont"
       :container/name "Huf"
       :child          {:child/id "child"}})
    => {:container/id {"cont" {:container/id   "cont"
                               :container/name "Huf"
                               :child          [:child/id "child"]}}
        :child/id     {"child" {:child/id   "child"
                                :child/name "Bla"}}}))

(specification "create-entity"
  (assertions
    (let [state (atom {})]
      (h/create-entity! {:state state}
        Container {:container/id "cont"
                   :child        {:child/id "child"}})
      @state)
    => {:container/id {"cont" {:state        :inited
                               :container/id "cont"
                               :child        [:child/id "child"]}}
        :child/id     {"child" {:child/id "child"}}}

    (let [state (atom {:parent {"pai" {:parent     "pai"
                                       :containers []}}})]
      (h/create-entity! {:state state
                         :ref   [:parent "pai"]}
        Container {:container/id "cont"
                   :some-data    "vai"
                   :child        {:child/id "child"}}
        :append :containers)
      @state)
    => {:parent       {"pai" {:parent     "pai"
                              :containers [[:container/id "cont"]]}}
        :container/id {"cont" {:state        :inited
                               :some-data    "vai"
                               :container/id "cont"
                               :child        [:child/id "child"]}}
        :child/id     {"child" {:child/id "child"}}}

    (let [state (atom {})]
      (h/create-entity! {:state state}
        Container ^::h/initialized {:container/id "cont" :foo "bar"})
      @state)
    => {:container/id {"cont" {:container/id "cont" :foo "bar"}}}))

(specification "deep-remove"
  (assertions
    (h/deep-remove-ref {} [:id 123])
    => {}

    (h/deep-remove-ref {:id {123 {:id 123}}} [:id 123])
    => {:id {}}

    (h/deep-remove-ref {:user {123 {:id      123
                                    :related [:user 321]}
                               321 {:id 321}}} [:user 123])
    => {:user {}}

    (h/deep-remove-ref {:user {123 {:id      123
                                    :related [[:user 321]
                                              [:user 42]]}
                               321 {:id 321}
                               42  {:id 42}
                               33  {:id 33}}} [:user 123])
    => {:user {33 {:id 33}}}))

(specification "vec-remove-index"
  (assertions
    (h/vec-remove-index 0 [5 6]) => [6]
    (h/vec-remove-index 1 [5 6]) => [5]
    (h/vec-remove-index 1 [:a :b :c]) => [:a :c]))
