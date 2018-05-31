(ns fulcro.inspect.lib.misc)

(defn fixed-size-assoc [size db key value]
  (let [{::keys [history] :as db'}
        (-> db
            (assoc key value)
            (update ::history (fnil conj []) key))]
    (if (> (count history) size)
      (-> db'
          (dissoc (first history))
          (update ::history #(vec (next %))))
      db')))
