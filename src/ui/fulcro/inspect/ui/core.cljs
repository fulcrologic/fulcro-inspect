(ns fulcro.inspect.ui.core
  (:require ["react-draggable" :refer [DraggableCore]]
            [clojure.string :as str]
            [com.fulcrologic.fulcro-css.css :as css]
            [com.fulcrologic.fulcro-css.localized-dom :as dom]
            [com.fulcrologic.fulcro.mutations :as fm]
            [com.fulcrologic.fulcro.components :as fc]
            [fulcro.inspect.ui.debounce-input :as di]
            [fulcro.inspect.ui.events :as events]
            [fulcro.inspect.ui.helpers :as h]
            [com.fulcrologic.fulcro.dom.icons :as icons]
            [garden.selectors :as gs]
            [goog.object :as gobj]))

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

(def box-shadow "0 6px 6px rgba(0, 0, 0, 0.26), 0 9px 20px rgba(0, 0, 0, 0.19)")

(def css-label-font
  {:font-family label-font-family
   :font-size   label-font-size})

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

(def css-flex-column
  {:flex           "1"
   :display        "flex"
   :flex-direction "column"})

(def css-triangle
  {:font-family    label-font-family
   :font-size      label-font-size
   :color          "#8f8f8f"
   :cursor         "pointer"
   :vertical-align "middle"
   :margin-right   "3px"})

(def css-code-font
  {:font-family "'courier new', monospace"
   :font-size   "12px"
   :white-space "nowrap"})

(def css-input
  [{:color       color-text-normal
    :border      "1px solid transparent"
    :outline     "0"
    :margin      "0 2px"
    :font-family label-font-family
    :font-size   label-font-size
    :padding     "6px"}
   [:&:hover {:border "1px solid #e0e0e0"}]
   [:&:focus {:border "1px solid #1973E7"}]])

;;; helpers

(defn add-zeros [n x]
  (loop [n (str n)]
    (if (< (count n) x)
      (recur (str 0 n))
      n)))

(defn print-timestamp [date]
  (if date
    (str (add-zeros (.getHours date) 2) ":"
      (add-zeros (.getMinutes date) 2) ":"
      (add-zeros (.getSeconds date) 2) ":"
      (add-zeros (.getMilliseconds date) 3))))

(defn component-class [comp-class css-selector]
  (if-let [class (get (css/get-classnames comp-class) (keyword (subs (name css-selector) 1)))]
    (keyword (str "$" class))))

;;; elements

(def icons-base64
  {:dock-right       "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAMCAYAAABSgIzaAAAAAXNSR0IArs4c6QAAAAlwSFlzAAALEwAACxMBAJqcGAAAA6ZpVFh0WE1MOmNvbS5hZG9iZS54bXAAAAAAADx4OnhtcG1ldGEgeG1sbnM6eD0iYWRvYmU6bnM6bWV0YS8iIHg6eG1wdGs9IlhNUCBDb3JlIDUuNC4wIj4KICAgPHJkZjpSREYgeG1sbnM6cmRmPSJodHRwOi8vd3d3LnczLm9yZy8xOTk5LzAyLzIyLXJkZi1zeW50YXgtbnMjIj4KICAgICAgPHJkZjpEZXNjcmlwdGlvbiByZGY6YWJvdXQ9IiIKICAgICAgICAgICAgeG1sbnM6eG1wPSJodHRwOi8vbnMuYWRvYmUuY29tL3hhcC8xLjAvIgogICAgICAgICAgICB4bWxuczp0aWZmPSJodHRwOi8vbnMuYWRvYmUuY29tL3RpZmYvMS4wLyIKICAgICAgICAgICAgeG1sbnM6ZXhpZj0iaHR0cDovL25zLmFkb2JlLmNvbS9leGlmLzEuMC8iPgogICAgICAgICA8eG1wOk1vZGlmeURhdGU+MjAxNy0xMi0yOVQxOToxMjowOTwveG1wOk1vZGlmeURhdGU+CiAgICAgICAgIDx4bXA6Q3JlYXRvclRvb2w+UGl4ZWxtYXRvciAzLjc8L3htcDpDcmVhdG9yVG9vbD4KICAgICAgICAgPHRpZmY6T3JpZW50YXRpb24+MTwvdGlmZjpPcmllbnRhdGlvbj4KICAgICAgICAgPHRpZmY6Q29tcHJlc3Npb24+NTwvdGlmZjpDb21wcmVzc2lvbj4KICAgICAgICAgPHRpZmY6UmVzb2x1dGlvblVuaXQ+MjwvdGlmZjpSZXNvbHV0aW9uVW5pdD4KICAgICAgICAgPHRpZmY6WVJlc29sdXRpb24+NzI8L3RpZmY6WVJlc29sdXRpb24+CiAgICAgICAgIDx0aWZmOlhSZXNvbHV0aW9uPjcyPC90aWZmOlhSZXNvbHV0aW9uPgogICAgICAgICA8ZXhpZjpQaXhlbFhEaW1lbnNpb24+MTQ8L2V4aWY6UGl4ZWxYRGltZW5zaW9uPgogICAgICAgICA8ZXhpZjpDb2xvclNwYWNlPjE8L2V4aWY6Q29sb3JTcGFjZT4KICAgICAgICAgPGV4aWY6UGl4ZWxZRGltZW5zaW9uPjEyPC9leGlmOlBpeGVsWURpbWVuc2lvbj4KICAgICAgPC9yZGY6RGVzY3JpcHRpb24+CiAgIDwvcmRmOlJERj4KPC94OnhtcG1ldGE+ChA7ceQAAAA5SURBVCgVY8zLy/vPQAZgIkMPWAsLTOOkSZMYYWx0GpuryLZxVCN68CLxyQ4cRmxxhGQwTibZNgIAuBEIq/65jKIAAAAASUVORK5CYII="
   :dock-right-blue  "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAMCAYAAABSgIzaAAAAAXNSR0IArs4c6QAAAAlwSFlzAAALEwAACxMBAJqcGAAAA6ZpVFh0WE1MOmNvbS5hZG9iZS54bXAAAAAAADx4OnhtcG1ldGEgeG1sbnM6eD0iYWRvYmU6bnM6bWV0YS8iIHg6eG1wdGs9IlhNUCBDb3JlIDUuNC4wIj4KICAgPHJkZjpSREYgeG1sbnM6cmRmPSJodHRwOi8vd3d3LnczLm9yZy8xOTk5LzAyLzIyLXJkZi1zeW50YXgtbnMjIj4KICAgICAgPHJkZjpEZXNjcmlwdGlvbiByZGY6YWJvdXQ9IiIKICAgICAgICAgICAgeG1sbnM6eG1wPSJodHRwOi8vbnMuYWRvYmUuY29tL3hhcC8xLjAvIgogICAgICAgICAgICB4bWxuczp0aWZmPSJodHRwOi8vbnMuYWRvYmUuY29tL3RpZmYvMS4wLyIKICAgICAgICAgICAgeG1sbnM6ZXhpZj0iaHR0cDovL25zLmFkb2JlLmNvbS9leGlmLzEuMC8iPgogICAgICAgICA8eG1wOk1vZGlmeURhdGU+MjAxNy0xMi0yOVQyMDoxMjo0NjwveG1wOk1vZGlmeURhdGU+CiAgICAgICAgIDx4bXA6Q3JlYXRvclRvb2w+UGl4ZWxtYXRvciAzLjc8L3htcDpDcmVhdG9yVG9vbD4KICAgICAgICAgPHRpZmY6T3JpZW50YXRpb24+MTwvdGlmZjpPcmllbnRhdGlvbj4KICAgICAgICAgPHRpZmY6Q29tcHJlc3Npb24+NTwvdGlmZjpDb21wcmVzc2lvbj4KICAgICAgICAgPHRpZmY6UmVzb2x1dGlvblVuaXQ+MjwvdGlmZjpSZXNvbHV0aW9uVW5pdD4KICAgICAgICAgPHRpZmY6WVJlc29sdXRpb24+NzI8L3RpZmY6WVJlc29sdXRpb24+CiAgICAgICAgIDx0aWZmOlhSZXNvbHV0aW9uPjcyPC90aWZmOlhSZXNvbHV0aW9uPgogICAgICAgICA8ZXhpZjpQaXhlbFhEaW1lbnNpb24+MTQ8L2V4aWY6UGl4ZWxYRGltZW5zaW9uPgogICAgICAgICA8ZXhpZjpDb2xvclNwYWNlPjE8L2V4aWY6Q29sb3JTcGFjZT4KICAgICAgICAgPGV4aWY6UGl4ZWxZRGltZW5zaW9uPjEyPC9leGlmOlBpeGVsWURpbWVuc2lvbj4KICAgICAgPC9yZGY6RGVzY3JpcHRpb24+CiAgIDwvcmRmOlJERj4KPC94OnhtcG1ldGE+Cs4JPgEAAAA4SURBVCgVY3RqePOfgQzARIYesBYWmMZ9DSKMMDY6jc1VZNs4qhE9eJH4ZAcOI7Y4QjIYJ5NsGwHiBAlzQbjUggAAAABJRU5ErkJggg=="
   :dock-bottom      "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAMCAIAAADd4huNAAAAAXNSR0IArs4c6QAAAAlwSFlzAAALEwAACxMBAJqcGAAAA6ZpVFh0WE1MOmNvbS5hZG9iZS54bXAAAAAAADx4OnhtcG1ldGEgeG1sbnM6eD0iYWRvYmU6bnM6bWV0YS8iIHg6eG1wdGs9IlhNUCBDb3JlIDUuNC4wIj4KICAgPHJkZjpSREYgeG1sbnM6cmRmPSJodHRwOi8vd3d3LnczLm9yZy8xOTk5LzAyLzIyLXJkZi1zeW50YXgtbnMjIj4KICAgICAgPHJkZjpEZXNjcmlwdGlvbiByZGY6YWJvdXQ9IiIKICAgICAgICAgICAgeG1sbnM6eG1wPSJodHRwOi8vbnMuYWRvYmUuY29tL3hhcC8xLjAvIgogICAgICAgICAgICB4bWxuczp0aWZmPSJodHRwOi8vbnMuYWRvYmUuY29tL3RpZmYvMS4wLyIKICAgICAgICAgICAgeG1sbnM6ZXhpZj0iaHR0cDovL25zLmFkb2JlLmNvbS9leGlmLzEuMC8iPgogICAgICAgICA8eG1wOk1vZGlmeURhdGU+MjAxNy0xMi0yOVQxOToxMjozODwveG1wOk1vZGlmeURhdGU+CiAgICAgICAgIDx4bXA6Q3JlYXRvclRvb2w+UGl4ZWxtYXRvciAzLjc8L3htcDpDcmVhdG9yVG9vbD4KICAgICAgICAgPHRpZmY6T3JpZW50YXRpb24+MTwvdGlmZjpPcmllbnRhdGlvbj4KICAgICAgICAgPHRpZmY6Q29tcHJlc3Npb24+NTwvdGlmZjpDb21wcmVzc2lvbj4KICAgICAgICAgPHRpZmY6UmVzb2x1dGlvblVuaXQ+MjwvdGlmZjpSZXNvbHV0aW9uVW5pdD4KICAgICAgICAgPHRpZmY6WVJlc29sdXRpb24+NzI8L3RpZmY6WVJlc29sdXRpb24+CiAgICAgICAgIDx0aWZmOlhSZXNvbHV0aW9uPjcyPC90aWZmOlhSZXNvbHV0aW9uPgogICAgICAgICA8ZXhpZjpQaXhlbFhEaW1lbnNpb24+MTQ8L2V4aWY6UGl4ZWxYRGltZW5zaW9uPgogICAgICAgICA8ZXhpZjpDb2xvclNwYWNlPjE8L2V4aWY6Q29sb3JTcGFjZT4KICAgICAgICAgPGV4aWY6UGl4ZWxZRGltZW5zaW9uPjEyPC9leGlmOlBpeGVsWURpbWVuc2lvbj4KICAgICAgPC9yZGY6RGVzY3JpcHRpb24+CiAgIDwvcmRmOlJERj4KPC94OnhtcG1ldGE+CgnaVK0AAAAtSURBVCgVY8zLy2MgDjARpwykigWidOLEiXj05OfnA2VJMHXAlTLSJLAG3FsAkVEFrYi1uDQAAAAASUVORK5CYII="
   :dock-bottom-blue "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAMCAYAAABSgIzaAAAAAXNSR0IArs4c6QAAAAlwSFlzAAALEwAACxMBAJqcGAAAA6ZpVFh0WE1MOmNvbS5hZG9iZS54bXAAAAAAADx4OnhtcG1ldGEgeG1sbnM6eD0iYWRvYmU6bnM6bWV0YS8iIHg6eG1wdGs9IlhNUCBDb3JlIDUuNC4wIj4KICAgPHJkZjpSREYgeG1sbnM6cmRmPSJodHRwOi8vd3d3LnczLm9yZy8xOTk5LzAyLzIyLXJkZi1zeW50YXgtbnMjIj4KICAgICAgPHJkZjpEZXNjcmlwdGlvbiByZGY6YWJvdXQ9IiIKICAgICAgICAgICAgeG1sbnM6eG1wPSJodHRwOi8vbnMuYWRvYmUuY29tL3hhcC8xLjAvIgogICAgICAgICAgICB4bWxuczp0aWZmPSJodHRwOi8vbnMuYWRvYmUuY29tL3RpZmYvMS4wLyIKICAgICAgICAgICAgeG1sbnM6ZXhpZj0iaHR0cDovL25zLmFkb2JlLmNvbS9leGlmLzEuMC8iPgogICAgICAgICA8eG1wOk1vZGlmeURhdGU+MjAxNy0xMi0yOVQyMDoxMjo4ODwveG1wOk1vZGlmeURhdGU+CiAgICAgICAgIDx4bXA6Q3JlYXRvclRvb2w+UGl4ZWxtYXRvciAzLjc8L3htcDpDcmVhdG9yVG9vbD4KICAgICAgICAgPHRpZmY6T3JpZW50YXRpb24+MTwvdGlmZjpPcmllbnRhdGlvbj4KICAgICAgICAgPHRpZmY6Q29tcHJlc3Npb24+NTwvdGlmZjpDb21wcmVzc2lvbj4KICAgICAgICAgPHRpZmY6UmVzb2x1dGlvblVuaXQ+MjwvdGlmZjpSZXNvbHV0aW9uVW5pdD4KICAgICAgICAgPHRpZmY6WVJlc29sdXRpb24+NzI8L3RpZmY6WVJlc29sdXRpb24+CiAgICAgICAgIDx0aWZmOlhSZXNvbHV0aW9uPjcyPC90aWZmOlhSZXNvbHV0aW9uPgogICAgICAgICA8ZXhpZjpQaXhlbFhEaW1lbnNpb24+MTQ8L2V4aWY6UGl4ZWxYRGltZW5zaW9uPgogICAgICAgICA8ZXhpZjpDb2xvclNwYWNlPjE8L2V4aWY6Q29sb3JTcGFjZT4KICAgICAgICAgPGV4aWY6UGl4ZWxZRGltZW5zaW9uPjEyPC9leGlmOlBpeGVsWURpbWVuc2lvbj4KICAgICAgPC9yZGY6RGVzY3JpcHRpb24+CiAgIDwvcmRmOlJERj4KPC94OnhtcG1ldGE+Cmpsn3gAAAA1SURBVCgVY3RqePOfgQzARIYesBYWmMZ9DSKMMDY+GuZCsm0cQhoZYZ7FFyDY5IaQH8l2KgCK6glzbJbgcgAAAABJRU5ErkJggg=="})

(defn fulcro-icon
  "Gets an SVG representation of the given icon. See material-icon-paths."
  [icon-name
   & {:keys [width height modifiers states className onClick title style]}]
  (assert (keyword? icon-name) "Icon name must be a keyword")
  (let [add-class  (fn [attrs])
        path-check (icon-name icons/material-icon-paths)
        icon-name  (str/replace (name icon-name) #"_" "-")]
    (when-not (str/blank? path-check)
      (dom/svg (cond->
                 {#_#_:classes (str/join " " [(icons/concat-class-string "c-icon" "--" modifiers)
                                              (str "c-icon--" icon-name)
                                              (icons/concat-state-string states)
                                              (icons/concat-class-string className)])
                  :version         "1.1"
                  :xmlns           "http://www.w3.org/2000/svg"
                  :width           "24"
                  :height          "24"
                  :aria-labelledby "title"
                  :role            "img"
                  :viewBox         "0 0 24 24"
                  :style           style}
                 onClick (assoc :onClick #(onClick)))
        (dom/title nil (str title))
        (dom/path #js {:d path-check})))))

(defn icon
  ([name] (icon {} name))
  ([props name]
   (if-let [code (get icons-base64 name)]
     (dom/img (h/props->html {:src code} props))
     (apply fulcro-icon name (apply concat props)))))

(def arrow-right "▶")
(def arrow-down "▼")

(fc/defsc Row [this props]
  {:css [[:.container {:display "flex"}
          [:&.align-start {:align-items "start"}]
          [:&.align-center {:align-items "center"}]
          [:&.align-baseline {:align-items "baseline"}]
          [:&.align-end {:align-items "end"}]]]}
  (dom/div :.container props (fc/children this)))

(def row (fc/factory Row))

(fc/defsc BreadcrumbItem
  [this props]
  {:css [[:.container {:cursor      "pointer"
                       :font-family label-font-family
                       :font-size   "15px"}]]}
  (dom/a :.container (merge {:href "#"} props) (fc/children this)))

(def breadcrumb-item (fc/factory BreadcrumbItem))

(fc/defsc Breadcrumb
  [this props]
  {:css         [[:.container {:display     "flex"
                               :align-items "baseline"
                               :flex-wrap   "wrap"}]
                 [:.separator {:color       "#b8b8b8"
                               :font-size   "24px"
                               :font-family "inherit"
                               :margin      "0 8px"
                               :font-weight "bold"
                               :position    "relative"
                               :top         "2px"}]]
   :css-include [BreadcrumbItem]}
  (dom/div :.container props (fc/children this)))

(def breadcrumb (fc/factory Breadcrumb))

(defn breadcrumb-separator []
  (dom/div {:classes [(component-class Breadcrumb :.separator)]} "›"))

(fc/defsc TableCell
  [this props]
  {:css [[:.container css-label-font
          {:border  "1px solid #d3d3d3"
           :padding "3px 5px"}]]}
  (dom/td :.container props (fc/children this)))

(def td (fc/factory TableCell))

(fc/defsc TableHead
  [this props]
  {:css [[:.container css-label-font
          {:background  "#f3f3f3"
           :border      "1px solid #d3d3d3"
           :color       "#000"
           :font-weight "normal"
           :padding     "8px 4px"
           :text-align  "left"}]]}
  (dom/th :.container props (fc/children this)))

(def th (fc/factory TableHead))

(fc/defsc TableRow
  [this props]
  {:css [[:.container {}
          [(gs/& (gs/nth-child 2))
           [(component-class TableCell :.container)
            {:background "#f5f5f5"}]]]]}
  (dom/tr :.container props (fc/children this)))

(def tr (fc/factory TableRow))

(fc/defsc TableBody
  [this props]
  {:css [[:.container {}]]}
  (dom/tbody :.container props (fc/children this)))

(def tbody (fc/factory TableBody))

(fc/defsc TableHeader
  [this props]
  {:css [[:.container {}]]}
  (dom/thead :.container props (fc/children this)))

(def thead (fc/factory TableHeader))

(fc/defsc Table
  [this props]
  {:css         [[:.container {:border          "1px solid #d3d3d3"
                               :border-collapse "collapse"
                               :width           "100%"}]]
   :css-include [TableHeader TableBody TableRow TableHead TableCell]}
  (dom/table :.container props (fc/children this)))

(def table (fc/factory Table))

(fc/defsc Code
  [this props]
  {:css [[:.container {:white-space "nowrap"
                       :font-family mono-font-family}]]}
  (dom/div :.container props (fc/children this)))

(def code (fc/factory Code))

(fc/defsc Button
  [this props]
  {:css [[:.button css-info-label
          {:background    "#fff"
           :border        "1px solid #ccc"
           :cursor        "pointer"
           :margin        "0"
           :color         "#1873E8"
           :border-radius "4px"
           :padding       "6px 12px"
           :font-size     "12px"}
          [:&:hover {:background "#f3f3f3"}]
          [:&:disabled {:cursor "default"
                        :color  "#adcbf7"}
           [:&:hover {:background "#fff"}]]

          [:&.primary {:background   "#1973e7"
                       :border-color "#2b7ce8"
                       :color        "#fff"}
           [:&:hover {:background "#3a86e8"}]
           [:&:disabled {:background   "#a7c5f1"
                         :border-color "#a3c2f1"}]]]]}
  (dom/button :.button props (fc/children this)))

(def button (fc/factory Button))

(defn primary-button [props & children]
  (apply button (update props :classes conj :.primary) children))

(fc/defsc Input
  [this props]
  {:css [`[:.input ~@css-input]]}
  (dom/input :.input props))

(def input (fc/factory Input))

(fc/defsc Label
  [this props]
  {:css [[:.label {:color        "#434C54"
                   :font-family  label-font-family
                   :font-weight  "normal"
                   :font-size    "13px"
                   :margin-right "10px"}]]}
  (dom/label :.label props (fc/children this)))

(def label (fc/factory Label))

(fc/defsc Header
  [this props]
  {:css [[:.header {:font-family    label-font-family
                    :font-weight    "normal"
                    :font-size      "22px"
                    :padding-bottom "14px"
                    :border-bottom  "1px solid #eee"}]]}
  (dom/h2 :.header props (fc/children this)))

(def header (fc/factory Header))

(fc/defsc Toggler
  [this props]
  {:css [[:.toggler {:border-radius "6px"
                     :cursor        "default"
                     :font-family   label-font-family
                     :font-weight   "normal"
                     :font-size     "13px"
                     :padding       "1px 4px"}
          [:&:hover {:background  "#c2c2c2"
                     :color       "#fff"
                     :text-shadow "0 1px #1b1b1b"}]
          [:&:active {:background  "#797979"
                      :color       "#fff"
                      :text-shadow "0 1px #1b1b1b"}]
          [:&.active {:background  "#aaa"
                      :color       "#fff"
                      :text-shadow "0 1px #1b1b1b"}]]]}
  (dom/div :.toggler props (fc/children this)))

(def toggler (fc/factory Toggler))

(fc/defsc ToolBar [this _]
  {:css (fn []
          [[:.container {:border-bottom "1px solid #dadada"
                         :display       "flex"
                         :align-items   "center"}
            [:$c-icon {:fill      color-icon-normal
                       :transform "scale(0.7)"}
             [:&:hover {:fill color-icon-strong}]]

            [:&.details {:background    "#f3f3f3"
                         :border-bottom "1px solid #ccc"
                         :display       "flex"
                         :align-items   "center"
                         :height        "28px"}]

            [(component-class Input :.input) {:padding "3px 6px"}]

            [(component-class Toggler :.toggler) {:margin "0 2px"}]]

           [:.action {:cursor      "pointer"
                      :display     "flex"
                      :align-items "center"}
            [(gs/& (gs/attr "disabled")) {:cursor "not-allowed"}
             [:$c-icon {:fill color-icon-normal}]]]

           [:.separator {:background "#ccc"
                         :width      "1px"
                         :height     "16px"
                         :margin     "0 6px"}]

           `[:.input ~@css-input]])}

  (let [css (css/get-classnames ToolBar)]
    (dom/div (h/props+classes this {:className (:container css)})
      (fc/children this))))

(def toolbar (fc/factory ToolBar))

(defn toolbar-separator []
  (dom/div #js {:className (:separator (css/get-classnames ToolBar))}))

(defn toolbar-spacer []
  (dom/div #js {:style #js {:flex 1}}))

(defn toolbar-action [props & children]
  (let [props (cond-> props (:disabled props) (dissoc :onClick))]
    (apply dom/div (h/props->html {:className (:action (css/get-classnames ToolBar))} props)
      children)))

(defn toolbar-text-field [props]
  (dom/input (h/props->html {:className (:input (css/get-classnames ToolBar))
                             :type      "text"} props)))

(defn toolbar-debounced-text-field [props]
  (di/debounce-input (merge {:className (:input (css/get-classnames ToolBar))
                             :type      "text"} props)))

(fc/defsc AutoFocusInput
  [this props]
  {:componentDidMount (fn [this] (.select ^js (dom/node this)))}
  (dom/input props))

(def auto-focus-input (fc/factory AutoFocusInput))

(fc/defsc InlineEditor
  [this {::keys [editing? editor-value]} {::keys [value on-change] :as computed} css]
  {:initial-state (fn [_]
                    {::editor-id    (random-uuid)
                     ::editing?     false
                     ::editor-value ""})
   :ident         [::editor-id ::editor-id]
   :query         [::editor-id ::editing? ::editor-value]
   :css           [[:.container {:flex 1}]
                   [:.no-label {:font-style "italic"
                                :color      color-text-faded}]
                   [:.label {:color       color-text-strong
                             :font-family label-font-family
                             :font-size   label-font-size}]
                   [:.input {:border     "1px solid #c7c7c7"
                             :box-shadow "0px 1px 3px 1px rgba(0, 0, 0, 0.078)"
                             :outline    "none"
                             :width      "100%"}]]
   :css-include   []}
  (dom/div :.container (h/props->html {:onClick #(when-not editing?
                                                   (fm/set-value! this ::editor-value value)
                                                   (fm/set-value! this ::editing? true))} computed)
    (if editing?
      (auto-focus-input
        {:className (:input css)
         :value     editor-value
         :onKeyDown #(cond
                       (events/match-key? % (events/key-code "escape"))
                       (fm/set-value! this ::editing? false)

                       (events/match-key? % (events/key-code "return"))
                       (do
                         (fm/set-value! this ::editing? false)
                         (on-change editor-value)))
         :onBlur    #(fm/set-value! this ::editing? false)
         :onChange  #(fm/set-string! this ::editor-value :event %)})
      (dom/div :.label
        (if (seq value) (str value) (dom/span :.no-label "Unnamed"))))))

(def inline-editor (h/computed-factory InlineEditor {:keyfn ::editor-id}))

(defn gen-space-classes [prop label value]
  "Generate spacing classes for a given property/value."
  (let [vpx (if (string? value) value (str value "px !important"))]
    [[(keyword (str "$" prop "-" label)) {(keyword (str prop "-top"))    vpx
                                          (keyword (str prop "-right"))  vpx
                                          (keyword (str prop "-bottom")) vpx
                                          (keyword (str prop "-left"))   vpx}]
     [(keyword (str "$" prop "-h-" label)) {(keyword (str prop "-right")) vpx
                                            (keyword (str prop "-left"))  vpx}]
     [(keyword (str "$" prop "-v-" label)) {(keyword (str prop "-top"))    vpx
                                            (keyword (str prop "-bottom")) vpx}]
     [(keyword (str "$" prop "-top-" label)) {(keyword (str prop "-top")) vpx}]
     [(keyword (str "$" prop "-right-" label)) {(keyword (str prop "-right")) vpx}]
     [(keyword (str "$" prop "-bottom-" label)) {(keyword (str prop "-bottom")) vpx}]
     [(keyword (str "$" prop "-left-" label)) {(keyword (str prop "-left")) vpx}]]))

(defn gen-all-spaces [values-map]
  (apply concat
    (for [[k v] values-map
          prop ["margin" "padding"]]
      (gen-space-classes prop k v))))

(def space-nano "0.5px")
(def space-micro "4px")
(def space-small "8px")
(def space-standard "16px")
(def space-medium "24px")
(def space-semi "32px")
(def space-large "48px")
(def space-x-large "64px")

(def spaces
  {"auto"     "auto !important"
   "none"     0
   "nano"     (str space-nano " !important")
   "micro"    (str space-micro " !important")
   "small"    (str space-small " !important")
   "standard" (str space-standard " !important")
   "medium"   (str space-medium " !important")
   "semi"     (str space-semi " !important")
   "large"    (str space-large " !important")
   "x-large"  (str space-x-large " !important")})

(fc/defsc CSS [_ _]
  {:css         [[:.focused-panel {:border-top     "1px solid #a3a3a3"
                                   :display        "flex"
                                   :flex-direction "column"
                                   :height         "50%"}]
                 [:.focused-container css-flex-column {:overflow "auto"
                                                       :padding  "0 10px"}]

                 [:a {:color           "#4183c4"
                      :text-decoration "none"}]

                 [:.info-group css-info-group
                  [(gs/& gs/first-child) {:border-top "0"}]]
                 [:.info-label css-info-label]
                 [:.flex {:flex "1"}]
                 [:$flex {:flex "1"}]
                 [:$flex-100 {:flex "1" :max-width "100%"}]
                 [:$highlight {:background "yellow"}]
                 [:.ident {:padding     "5px 6px"
                           :background  "#f3f3f3"
                           :color       "#424242"
                           :display     "inline-block"
                           :font-family mono-font-family
                           :font-size   label-font-size}]
                 [:.display-name {:background  "#e5efff"
                                  :color       "#051d38"
                                  :display     "inline-block"
                                  :padding     "4px 8px"
                                  :font-family mono-font-family
                                  :font-size   "14px"}]
                 (gen-all-spaces spaces)]
   :css-include [ToolBar Row InlineEditor Button Header Input Label Toggler
                 Breadcrumb Table Code]})

(def scss (css/get-classnames CSS))

(defn focus-panel [props & children]
  (apply dom/div (h/props->html {:className (:focused-panel scss)}
                   props)
    children))

(defn focus-panel-content [props & children]
  (apply dom/div (h/props->html {:className (:focused-container scss)}
                   props)
    children))

(defn info [{::keys [title] :as props} & children]
  (apply dom/div (h/props->html {:className (:info-group scss)} props)
    (if title
      (dom/div #js {:className (:info-label scss)} title))
    children))

(defn ident [props ref]
  (dom/div (h/props->html {:className (:ident scss)} props)
    (pr-str ref)))

(defn comp-display-name [props display-name]
  (dom/div (h/props->html {:className (:display-name scss)} props)
    (str display-name)))

(defn drag-resize [this {:keys [attribute default axis props] :or {axis "y"}} child]
  (js/React.createElement DraggableCore
    #js {:key     "dragHandler"
         :onStart (fn [e dd]
                    (gobj/set this "start" (gobj/get dd axis))
                    (gobj/set this "startSize" (or (fc/get-state this attribute) default)))
         :onDrag  (fn [e dd]
                    (let [start    (gobj/get this "start")
                          size     (gobj/get this "startSize")
                          value    (gobj/get dd axis)
                          new-size (+ size (if (= "x" axis) (- value start) (- start value)))]
                      (fc/set-state! this {attribute new-size})))}
    (dom/div (merge {:style {:pointerEvents "all"
                             :cursor        (if (= "x" axis) "ew-resize" "ns-resize")}}
               props)
      child)))
