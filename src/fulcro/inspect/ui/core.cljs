(ns fulcro.inspect.ui.core)

(def mono-font-family "monospace")

(def label-font-family "sans-serif")
(def label-font-size "12px")

(def color-bg-light "#f5f5f5")
(def color-bg-light-border "#e1e1e1")
(def color-bg-medium-border "#cdcdcd")

(def color-text-normal "#5a5a5a")
(def color-text-strong "#333")
(def color-text-faded "#bbb")

(def color-icon-normal "#6e6e6e")
(def color-icon-strong "#333")

(def css-timestamp
  {:font-family "monospace"
   :font-size   "11px"
   :color       "#808080"
   :margin      "0 4px 0 7px"})

;;; helpers

(defn add-zeros [n x]
  (loop [n (str n)]
    (if (< (count n) x)
      (recur (str 0 n))
      n)))

(defn print-timestamp [date]
  (str (add-zeros (.getHours date) 2) ":"
       (add-zeros (.getMinutes date) 2) ":"
       (add-zeros (.getSeconds date) 2) ":"
       (add-zeros (.getMilliseconds date) 3)))
