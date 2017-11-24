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

(def color-row-hover "#eef3fa")
(def color-row-selected "#e6e6e6")

(def css-info-group
  {:border-top "1px solid #eee"
   :padding    "7px 0"})

(def css-info-label
  {:color         color-text-normal
   :margin-bottom "6px"
   :font-weight   "bold"
   :font-family   label-font-family
   :font-size     "13px"})

(def css-timestamp
  {:font-family "monospace"
   :font-size   "11px"
   :color       "#808080"
   :margin      "0 4px 0 7px"})

(def css-dock-details-container
  {:border-top     "1px solid #a3a3a3"
   :display        "flex"
   :flex-direction "column"
   :height         "50%"})

(def css-dock-details-item-container
  {:flex     "1"
   :overflow "auto"
   :padding  "0 10px"})

(def css-dock-details-tools
  {:background    "#f3f3f3"
   :border-bottom "1px solid #ccc"
   :display       "flex"
   :align-items   "center"
   :height        "28px"})

(def css-icon
  {:padding     "1px 7px"
   :cursor      "pointer"
   :color       "transparent"
   :text-shadow (str "0 0 0 " color-icon-normal)})

(def css-icon-close
  {:font-size     "9px"
   :padding-right "12px"})

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
