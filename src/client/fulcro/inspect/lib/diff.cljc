(ns fulcro.inspect.lib.diff
  (:require [clojure.spec.alpha :as s]))

(s/def ::updates map?)
(s/def ::removals vector?)

(defn updates [a b]
  (reduce
    (fn [adds [k v]]
      (let [vb (get a k ::unset)]
        (if (= v vb)
          adds
          (if (and (map? v) (map? vb))
            (assoc adds k (updates vb v))
            (assoc adds k v)))))
    {}
    b))

(defn removals [a b]
  (reduce
    (fn [rems [k v]]
      (if-let [[_ vb] (find b k)]
        (if (and (map? v) (map? vb))
          (let [childs (removals v vb)]
            (if (seq childs)
              (conj rems {k childs})
              rems))
          rems)
        (conj rems k)))
    []
    a))

(defn diff [a b]
  {::updates  (updates a b)
   ::removals (removals a b)})

(defn deep-merge [x y]
  (if (and (map? x) (map? y))
    (merge-with deep-merge x y)
    y))

(defn patch-updates [x {::keys [updates]}]
  (merge-with deep-merge x updates))

(defn patch-removals [x {::keys [removals]}]
  (reduce
    (fn [final rem]
      (if (map? rem)
        (let [[k v] (first rem)]
          (update final k #(patch-removals % {::removals v})))
        (dissoc final rem)))
    x
    removals))

(defn patch [x diff]
  (-> x
      (patch-updates diff)
      (patch-removals diff)))
