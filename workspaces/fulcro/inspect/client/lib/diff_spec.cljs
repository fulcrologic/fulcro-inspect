(ns fulcro.inspect.client.lib.diff-spec
  (:require [nubank.workspaces.core :refer [deftest]]
            [cljs.test :refer [is]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as props]
            [fulcro.inspect.lib.diff :as diff]))

(deftest test-updates
  (is (= (diff/updates {} {})
         {}))
  (is (= (diff/updates {} {:a 1})
         {:a 1}))
  (is (= (diff/updates {} {:a nil})
         {:a nil}))
  (is (= (diff/updates {:a 1} {:a 1})
         {}))
  (is (= (diff/updates {:a 1 :b 2} {:a 3 :c 5})
         {:a 3 :c 5}))
  (is (= (diff/updates {:a 1 :b 2} {:a 3 :c 5})
         {:a 3 :c 5}))
  (is (= (diff/updates {:a {:b 1 :c 2}} {:a {:b 1 :d 3}})
         {:a {:d 3}})))

(deftest test-removals
  (is (= (diff/removals {} {})
         []))
  (is (= (diff/removals {} {:a 1})
         []))
  (is (= (diff/removals {:a 1} {:a 1})
         []))
  (is (= (diff/removals {:a 1 :b 2} {:a 3 :c 5})
         [:b]))
  (is (= (diff/removals {:a {:b 1 :c 2}} {:a {:b 1 :d 3}})
         [{:a [:c]}]))
  (is (= (diff/removals {:a {:b 1 :c 2}} {:a {:b 1 :c 2 :d 3}})
         []))
  (is (= (diff/removals {:a {{:table "x" :id 1} {:foo "bar"}
                             {:table "x" :id 2} {:foo "baz"}}}
                        {:a {{:table "x" :id 2} {}}})
         [{:a [{:table "x" :id 1 ::diff/key? true}
               {{:table "x" :id 2} [:foo]}]}])))

(defn patch-test [a b]
  (let [delta (diff/diff a b)]
    (diff/patch a delta)))

(deftest test-patch-data
  (is (= (patch-test {} {})
         {}))
  (is (= (patch-test {} {:a 1})
         {:a 1}))
  (is (= (patch-test {:a 1} {:a 1})
         {:a 1}))
  (is (= (patch-test {:a 1 :b 2} {:a 3 :c 5})
         {:a 3 :c 5}))
  (is (= (patch-test {:a {:b 1 :c 2}} {:a {:b 1 :d 3}})
         {:a {:b 1 :d 3}}))
  (is (= (patch-test {:a {:b 1 :c 2}} {:a {:b 1 :c 2 :d 3}})
         {:a {:b 1 :c 2 :d 3}}))
  (is (= (patch-test {:a {{:table "x" :id 1} {:foo "bar"}
                          {:table "x" :id 2} {:foo "baz"}}}
                     {:a {{:table "x" :id 2} {:foo "baz"}}})
         {:a {{:table "x" :id 2} {:foo "baz"}}}))
  (is (= (patch-test {:a {{:table "x" :id 1} {:foo "bar" :extra "value"}}}
                     {:a {{:table "x" :id 1} {:foo "baz"}}})
         {:a {{:table "x" :id 1} {:foo "baz"}}}))
  (is (= (patch-test {:a {1 {:config [:other-id {:table :a :id 1}]}}}
                     {:a {1 {}}})
         {:a {1 {}}})))

(def generators
  {::gen-db-root
   (fn [{::keys [gen-root-value] :as env}]
     (gen/map gen/keyword (gen-root-value env)))

   ::gen-root-value
   (fn [{::keys [gen-table gen-ident] :as env}]
     (gen/frequency [[10 (gen-table env)]
                     [1 (gen-ident env)]]))

   ::gen-table
   (fn [{::keys [gen-entity] :as env}]
     (gen/map gen/uuid (gen-entity env)))

   ::gen-entity
   (fn [{::keys [gen-entity-value] :as env}]
     (gen/map gen/keyword (gen-entity-value env)))

   ::gen-entity-value
   (fn [{::keys [gen-ident] :as env}]
     (gen/frequency [[10 gen/simple-type-printable]
                     [1 (gen/vector (gen-ident env) 0 50)]]))

   ::gen-ident
   (fn [{::keys [gen-ident-value] :as env}]
     (gen/tuple gen/keyword (gen-ident-value env)))

   ::gen-ident-value
   (fn [_]
     (gen/frequency [[50 gen/uuid]
                     [20 (gen/return nil)]
                     [5 gen/simple-type-printable]
                     [1 (gen/tuple gen/uuid gen/string-alphanumeric)]]))})

(defn make-gen [env name]
  (let [env (merge generators env)
        gen (get env name)]
    (assert gen (str "No generator available for " name))
    ((get env name) env)))

(defn differ-props [env]
  (props/for-all [a (make-gen {} ::gen-db-root)
                  b (make-gen {} ::gen-db-root)]
    (= (patch-test a b)
       b)))

(comment
  (gen/sample (make-gen {} ::gen-db-root))

  (patch-test {} {:x 1})

  (let [a {}
        b {:x 1}]
    (set/difference a b))

  (tc/quick-check 300 (differ-props {}) :max-size 10))
