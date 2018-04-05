(ns fulcro.inspect.ui.core
  (:require [fulcro.client.primitives :as fp]
            [fulcro-css.css :as css]
            [fulcro.ui.icons :as icons]
            [fulcro.inspect.ui.helpers :as h]
            [garden.selectors :as gs]
            [fulcro.client.dom :as dom]
            [clojure.string :as str]))

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

;;; elements

(def icons-base64
  {:dock-right "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAMCAYAAABSgIzaAAAAAXNSR0IArs4c6QAAAAlwSFlzAAALEwAACxMBAJqcGAAAA6ZpVFh0WE1MOmNvbS5hZG9iZS54bXAAAAAAADx4OnhtcG1ldGEgeG1sbnM6eD0iYWRvYmU6bnM6bWV0YS8iIHg6eG1wdGs9IlhNUCBDb3JlIDUuNC4wIj4KICAgPHJkZjpSREYgeG1sbnM6cmRmPSJodHRwOi8vd3d3LnczLm9yZy8xOTk5LzAyLzIyLXJkZi1zeW50YXgtbnMjIj4KICAgICAgPHJkZjpEZXNjcmlwdGlvbiByZGY6YWJvdXQ9IiIKICAgICAgICAgICAgeG1sbnM6eG1wPSJodHRwOi8vbnMuYWRvYmUuY29tL3hhcC8xLjAvIgogICAgICAgICAgICB4bWxuczp0aWZmPSJodHRwOi8vbnMuYWRvYmUuY29tL3RpZmYvMS4wLyIKICAgICAgICAgICAgeG1sbnM6ZXhpZj0iaHR0cDovL25zLmFkb2JlLmNvbS9leGlmLzEuMC8iPgogICAgICAgICA8eG1wOk1vZGlmeURhdGU+MjAxNy0xMi0yOVQxOToxMjowOTwveG1wOk1vZGlmeURhdGU+CiAgICAgICAgIDx4bXA6Q3JlYXRvclRvb2w+UGl4ZWxtYXRvciAzLjc8L3htcDpDcmVhdG9yVG9vbD4KICAgICAgICAgPHRpZmY6T3JpZW50YXRpb24+MTwvdGlmZjpPcmllbnRhdGlvbj4KICAgICAgICAgPHRpZmY6Q29tcHJlc3Npb24+NTwvdGlmZjpDb21wcmVzc2lvbj4KICAgICAgICAgPHRpZmY6UmVzb2x1dGlvblVuaXQ+MjwvdGlmZjpSZXNvbHV0aW9uVW5pdD4KICAgICAgICAgPHRpZmY6WVJlc29sdXRpb24+NzI8L3RpZmY6WVJlc29sdXRpb24+CiAgICAgICAgIDx0aWZmOlhSZXNvbHV0aW9uPjcyPC90aWZmOlhSZXNvbHV0aW9uPgogICAgICAgICA8ZXhpZjpQaXhlbFhEaW1lbnNpb24+MTQ8L2V4aWY6UGl4ZWxYRGltZW5zaW9uPgogICAgICAgICA8ZXhpZjpDb2xvclNwYWNlPjE8L2V4aWY6Q29sb3JTcGFjZT4KICAgICAgICAgPGV4aWY6UGl4ZWxZRGltZW5zaW9uPjEyPC9leGlmOlBpeGVsWURpbWVuc2lvbj4KICAgICAgPC9yZGY6RGVzY3JpcHRpb24+CiAgIDwvcmRmOlJERj4KPC94OnhtcG1ldGE+ChA7ceQAAAA5SURBVCgVY8zLy/vPQAZgIkMPWAsLTOOkSZMYYWx0GpuryLZxVCN68CLxyQ4cRmxxhGQwTibZNgIAuBEIq/65jKIAAAAASUVORK5CYII="
   :dock-right-blue "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAMCAYAAABSgIzaAAAAAXNSR0IArs4c6QAAAAlwSFlzAAALEwAACxMBAJqcGAAAA6ZpVFh0WE1MOmNvbS5hZG9iZS54bXAAAAAAADx4OnhtcG1ldGEgeG1sbnM6eD0iYWRvYmU6bnM6bWV0YS8iIHg6eG1wdGs9IlhNUCBDb3JlIDUuNC4wIj4KICAgPHJkZjpSREYgeG1sbnM6cmRmPSJodHRwOi8vd3d3LnczLm9yZy8xOTk5LzAyLzIyLXJkZi1zeW50YXgtbnMjIj4KICAgICAgPHJkZjpEZXNjcmlwdGlvbiByZGY6YWJvdXQ9IiIKICAgICAgICAgICAgeG1sbnM6eG1wPSJodHRwOi8vbnMuYWRvYmUuY29tL3hhcC8xLjAvIgogICAgICAgICAgICB4bWxuczp0aWZmPSJodHRwOi8vbnMuYWRvYmUuY29tL3RpZmYvMS4wLyIKICAgICAgICAgICAgeG1sbnM6ZXhpZj0iaHR0cDovL25zLmFkb2JlLmNvbS9leGlmLzEuMC8iPgogICAgICAgICA8eG1wOk1vZGlmeURhdGU+MjAxNy0xMi0yOVQyMDoxMjo0NjwveG1wOk1vZGlmeURhdGU+CiAgICAgICAgIDx4bXA6Q3JlYXRvclRvb2w+UGl4ZWxtYXRvciAzLjc8L3htcDpDcmVhdG9yVG9vbD4KICAgICAgICAgPHRpZmY6T3JpZW50YXRpb24+MTwvdGlmZjpPcmllbnRhdGlvbj4KICAgICAgICAgPHRpZmY6Q29tcHJlc3Npb24+NTwvdGlmZjpDb21wcmVzc2lvbj4KICAgICAgICAgPHRpZmY6UmVzb2x1dGlvblVuaXQ+MjwvdGlmZjpSZXNvbHV0aW9uVW5pdD4KICAgICAgICAgPHRpZmY6WVJlc29sdXRpb24+NzI8L3RpZmY6WVJlc29sdXRpb24+CiAgICAgICAgIDx0aWZmOlhSZXNvbHV0aW9uPjcyPC90aWZmOlhSZXNvbHV0aW9uPgogICAgICAgICA8ZXhpZjpQaXhlbFhEaW1lbnNpb24+MTQ8L2V4aWY6UGl4ZWxYRGltZW5zaW9uPgogICAgICAgICA8ZXhpZjpDb2xvclNwYWNlPjE8L2V4aWY6Q29sb3JTcGFjZT4KICAgICAgICAgPGV4aWY6UGl4ZWxZRGltZW5zaW9uPjEyPC9leGlmOlBpeGVsWURpbWVuc2lvbj4KICAgICAgPC9yZGY6RGVzY3JpcHRpb24+CiAgIDwvcmRmOlJERj4KPC94OnhtcG1ldGE+Cs4JPgEAAAA4SURBVCgVY3RqePOfgQzARIYesBYWmMZ9DSKMMDY6jc1VZNs4qhE9eJH4ZAcOI7Y4QjIYJ5NsGwHiBAlzQbjUggAAAABJRU5ErkJggg=="
   :dock-bottom "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAMCAIAAADd4huNAAAAAXNSR0IArs4c6QAAAAlwSFlzAAALEwAACxMBAJqcGAAAA6ZpVFh0WE1MOmNvbS5hZG9iZS54bXAAAAAAADx4OnhtcG1ldGEgeG1sbnM6eD0iYWRvYmU6bnM6bWV0YS8iIHg6eG1wdGs9IlhNUCBDb3JlIDUuNC4wIj4KICAgPHJkZjpSREYgeG1sbnM6cmRmPSJodHRwOi8vd3d3LnczLm9yZy8xOTk5LzAyLzIyLXJkZi1zeW50YXgtbnMjIj4KICAgICAgPHJkZjpEZXNjcmlwdGlvbiByZGY6YWJvdXQ9IiIKICAgICAgICAgICAgeG1sbnM6eG1wPSJodHRwOi8vbnMuYWRvYmUuY29tL3hhcC8xLjAvIgogICAgICAgICAgICB4bWxuczp0aWZmPSJodHRwOi8vbnMuYWRvYmUuY29tL3RpZmYvMS4wLyIKICAgICAgICAgICAgeG1sbnM6ZXhpZj0iaHR0cDovL25zLmFkb2JlLmNvbS9leGlmLzEuMC8iPgogICAgICAgICA8eG1wOk1vZGlmeURhdGU+MjAxNy0xMi0yOVQxOToxMjozODwveG1wOk1vZGlmeURhdGU+CiAgICAgICAgIDx4bXA6Q3JlYXRvclRvb2w+UGl4ZWxtYXRvciAzLjc8L3htcDpDcmVhdG9yVG9vbD4KICAgICAgICAgPHRpZmY6T3JpZW50YXRpb24+MTwvdGlmZjpPcmllbnRhdGlvbj4KICAgICAgICAgPHRpZmY6Q29tcHJlc3Npb24+NTwvdGlmZjpDb21wcmVzc2lvbj4KICAgICAgICAgPHRpZmY6UmVzb2x1dGlvblVuaXQ+MjwvdGlmZjpSZXNvbHV0aW9uVW5pdD4KICAgICAgICAgPHRpZmY6WVJlc29sdXRpb24+NzI8L3RpZmY6WVJlc29sdXRpb24+CiAgICAgICAgIDx0aWZmOlhSZXNvbHV0aW9uPjcyPC90aWZmOlhSZXNvbHV0aW9uPgogICAgICAgICA8ZXhpZjpQaXhlbFhEaW1lbnNpb24+MTQ8L2V4aWY6UGl4ZWxYRGltZW5zaW9uPgogICAgICAgICA8ZXhpZjpDb2xvclNwYWNlPjE8L2V4aWY6Q29sb3JTcGFjZT4KICAgICAgICAgPGV4aWY6UGl4ZWxZRGltZW5zaW9uPjEyPC9leGlmOlBpeGVsWURpbWVuc2lvbj4KICAgICAgPC9yZGY6RGVzY3JpcHRpb24+CiAgIDwvcmRmOlJERj4KPC94OnhtcG1ldGE+CgnaVK0AAAAtSURBVCgVY8zLy2MgDjARpwykigWidOLEiXj05OfnA2VJMHXAlTLSJLAG3FsAkVEFrYi1uDQAAAAASUVORK5CYII="
   :dock-bottom-blue "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAMCAYAAABSgIzaAAAAAXNSR0IArs4c6QAAAAlwSFlzAAALEwAACxMBAJqcGAAAA6ZpVFh0WE1MOmNvbS5hZG9iZS54bXAAAAAAADx4OnhtcG1ldGEgeG1sbnM6eD0iYWRvYmU6bnM6bWV0YS8iIHg6eG1wdGs9IlhNUCBDb3JlIDUuNC4wIj4KICAgPHJkZjpSREYgeG1sbnM6cmRmPSJodHRwOi8vd3d3LnczLm9yZy8xOTk5LzAyLzIyLXJkZi1zeW50YXgtbnMjIj4KICAgICAgPHJkZjpEZXNjcmlwdGlvbiByZGY6YWJvdXQ9IiIKICAgICAgICAgICAgeG1sbnM6eG1wPSJodHRwOi8vbnMuYWRvYmUuY29tL3hhcC8xLjAvIgogICAgICAgICAgICB4bWxuczp0aWZmPSJodHRwOi8vbnMuYWRvYmUuY29tL3RpZmYvMS4wLyIKICAgICAgICAgICAgeG1sbnM6ZXhpZj0iaHR0cDovL25zLmFkb2JlLmNvbS9leGlmLzEuMC8iPgogICAgICAgICA8eG1wOk1vZGlmeURhdGU+MjAxNy0xMi0yOVQyMDoxMjo4ODwveG1wOk1vZGlmeURhdGU+CiAgICAgICAgIDx4bXA6Q3JlYXRvclRvb2w+UGl4ZWxtYXRvciAzLjc8L3htcDpDcmVhdG9yVG9vbD4KICAgICAgICAgPHRpZmY6T3JpZW50YXRpb24+MTwvdGlmZjpPcmllbnRhdGlvbj4KICAgICAgICAgPHRpZmY6Q29tcHJlc3Npb24+NTwvdGlmZjpDb21wcmVzc2lvbj4KICAgICAgICAgPHRpZmY6UmVzb2x1dGlvblVuaXQ+MjwvdGlmZjpSZXNvbHV0aW9uVW5pdD4KICAgICAgICAgPHRpZmY6WVJlc29sdXRpb24+NzI8L3RpZmY6WVJlc29sdXRpb24+CiAgICAgICAgIDx0aWZmOlhSZXNvbHV0aW9uPjcyPC90aWZmOlhSZXNvbHV0aW9uPgogICAgICAgICA8ZXhpZjpQaXhlbFhEaW1lbnNpb24+MTQ8L2V4aWY6UGl4ZWxYRGltZW5zaW9uPgogICAgICAgICA8ZXhpZjpDb2xvclNwYWNlPjE8L2V4aWY6Q29sb3JTcGFjZT4KICAgICAgICAgPGV4aWY6UGl4ZWxZRGltZW5zaW9uPjEyPC9leGlmOlBpeGVsWURpbWVuc2lvbj4KICAgICAgPC9yZGY6RGVzY3JpcHRpb24+CiAgIDwvcmRmOlJERj4KPC94OnhtcG1ldGE+Cmpsn3gAAAA1SURBVCgVY3RqePOfgQzARIYesBYWmMZ9DSKMMDY+GuZCsm0cQhoZYZ7FFyDY5IaQH8l2KgCK6glzbJbgcgAAAABJRU5ErkJggg=="})

(defn fulcro-icon
  "Gets an SVG representation of the given icon. See material-icon-paths."
  [icon-name
   & {:keys [width height modifiers states className onClick title]}]
  (assert (keyword? icon-name) "Icon name must be a keyword")
  (let [add-class  (fn [attrs])
        path-check (icon-name icons/material-icon-paths)
        icon-name  (str/replace (name icon-name) #"_" "-")]
    (when-not (str/blank? path-check)
      (dom/svg (clj->js
                 (cond->
                   {:className       (str/join " " [(icons/concat-class-string "c-icon" "--" modifiers)
                                                    (str "c-icon--" icon-name)
                                                    (icons/concat-state-string states)
                                                    (icons/concat-class-string className)])
                    :version         "1.1"
                    :xmlns           "http://www.w3.org/2000/svg"
                    :width           "24"
                    :height          "24"
                    :aria-labelledby "title"
                    :role            "img"
                    :viewBox         "0 0 24 24"}
                   onClick (assoc :onClick #(onClick))))
        (dom/title nil (str (or title (icons/title-case (str/replace (name icon-name) #"_" " ")))))
        (dom/path #js {:d path-check})))))

(defn icon
  ([name] (icon {} name))
  ([props name]
   (if-let [code (get icons-base64 name)]
     (dom/img (h/props->html {:src code} props))
     (apply fulcro-icon name (apply concat props)))))

(fp/defui ^:once ToolBar
  static css/CSS
  (local-rules [_] [[:.container {:border-bottom "1px solid #dadada"
                                  :display       "flex"
                                  :align-items   "center"}
                     [:$c-icon {:fill      color-icon-normal
                                :transform "scale(0.7)"}
                      [:&:hover {:fill color-icon-strong}]]

                     [:&.details {:background    "#f3f3f3"
                                  :border-bottom "1px solid #ccc"
                                  :display       "flex"
                                  :align-items   "center"
                                  :height        "28px"}]]

                    [:.action {:cursor      "pointer"
                               :display     "flex"
                               :align-items "center"}
                     [(gs/& (gs/attr "disabled")) {:cursor "not-allowed"}
                      [:$c-icon {:fill color-icon-normal}]]]

                    [:.separator {:background "#ccc"
                                  :width      "1px"
                                  :height     "16px"
                                  :margin     "0 6px"}]

                    [:.input {:color       color-text-normal
                              :outline     "0"
                              :margin      "0 2px"
                              :font-family label-font-family
                              :font-size   label-font-size
                              :padding     "2px 4px"}]])
  (include-children [_] [])

  Object
  (render [this]
    (let [css (css/get-classnames ToolBar)]
      (dom/div (h/props+classes this {:className (:container css)})
        (fp/children this)))))

(def toolbar (fp/factory ToolBar))

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

(fp/defui ^:once CSS
  static css/CSS
  (local-rules [_] [[:.focused-panel {:border-top     "1px solid #a3a3a3"
                                      :display        "flex"
                                      :flex-direction "column"
                                      :height         "50%"}]
                    [:.focused-container {:flex     "1"
                                          :overflow "auto"
                                          :padding  "0 10px"}]

                    [:.info-group css-info-group
                     [(gs/& gs/first-child) {:border-top "0"}]]
                    [:.info-label css-info-label]
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
                                     :font-size   "14px"}]])
  (include-children [_] [ToolBar]))

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
