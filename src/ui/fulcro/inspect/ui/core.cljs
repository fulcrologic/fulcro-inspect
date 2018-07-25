(ns fulcro.inspect.ui.core
  (:require [clojure.string :as str]
            [fulcro.client.primitives :as fp]
            [fulcro-css.css :as css]
            [fulcro.ui.icons :as icons]
            [fulcro.inspect.ui.helpers :as h]
            [fulcro.client.mutations :as fm]
            [fulcro.client.localized-dom :as dom]
            [fulcro.inspect.ui.events :as events]
            [garden.selectors :as gs]))

(def mono-font-family "monospace")

(def label-font-family "sans-serif")
(def label-font-size "12px")

(def colors (if (= (.. js/chrome -devtools -panels -themeName) "dark")
              {:bg               "#242424"
               :bg-light         "#333"
               :bg-medium        "#333"
               :bg-container     "#333"
               :bg-light-border  "#616161"
               :bg-medium-border "#cdcdcd"

               :text             "#fafafa"
               :text-secondary   "#ccc"
               :text-normal      "#ccc"
               :text-strong      "#fafafa"
               :text-faded       "#bbb"
               :text-table       "#313942"

               :icon-normal      "#aaa"
               :icon-strong      "#ddd"
               :icon-triangle    "#aaa"

               :row-hover        "#12243d"
               :row-selected     "#2f84da"

               :nil              "#5db0d7"
               :string           "#ec8b4f"
               :keyword          "#967fcf"
               :symbol           "#d3d9d3"
               :number           "#ec8b4f"
               :boolean          "#5db0d7"

               :error            "#e80000"

               :chart-bg-flame   "#f6f7f8"}

              {:bg               "#fff"
               :bg-light         "#f5f5f5"
               :bg-medium        "#ddd"
               :bg-container     "rgba(100, 255, 100, 0.08)"
               :bg-light-border  "#e1e1e1"
               :bg-medium-border "#cdcdcd"

               :text             "#000"
               :text-secondary   "#808080"
               :text-normal      "5a5a5a"
               :text-strong      "#333"
               :text-faded       "#bbb"
               :text-table       "#313942"

               :icon-normal      "#6e6e6e"
               :icon-strong      "#333"
               :icon-triangle    "#8f8f8f"

               :row-hover        "#eef3fa"
               :row-selected     "#e6e6e6"

               :nil              "#808080"
               :string           "#c41a16"
               :keyword          "#881391"
               :symbol           "#134f91"
               :number           "#1c00cf"
               :boolean          "#009999"

               :error            "#e80000"

               :chart-bg-flame   "#f6f7f8"}))

(def box-shadow "0 6px 6px rgba(0, 0, 0, 0.26), 0 9px 20px rgba(0, 0, 0, 0.19)")

(def css-info-group
  {:border-top (str "1px solid " (:bg-light-border colors))
   :padding    "7px 0"})

(def css-info-label
  {:color         (:text-normal colors)
   :margin-bottom "6px"
   :font-weight   "bold"
   :font-family   label-font-family
   :font-size     "13px"})

(def css-timestamp
  {:font-family "monospace"
   :font-size   "11px"
   :color       (:text-secondary colors)
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

(defn foreign-class [comp class]
  (->> (css/get-classnames comp) class (str "$") keyword))

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
                    :viewBox         "0 0 24 24"
                    :style           style}
                   onClick (assoc :onClick #(onClick))))
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

(fp/defsc Row [this props]
  {:css [[:.container {:display "flex"}]]}
  (dom/div :.container props (fp/children this)))

(def row (fp/factory Row))

(fp/defsc ToolBar [this _]
  {:css [[:.container {:border-bottom (str "1px solid " (:bg-light-border colors))
                       :display       "flex"
                       :align-items   "center"
                       :color         (:text colors)}
          [:$c-icon {:fill      (:icon-normal colors)
                     :transform "scale(0.7)"}
           [:&:hover {:fill (:icon-strong colors)}]]

          [:&.details {:background    (:bg-light colors)
                       :border-bottom (str "1px solid " (:bg-medium-border colors))
                       :display       "flex"
                       :align-items   "center"
                       :height        "28px"}]]

         [:.action {:cursor      "pointer"
                    :display     "flex"
                    :align-items "center"}
          [(gs/& (gs/attr "disabled")) {:cursor "not-allowed"}
           [:$c-icon {:fill (:icon-normal colors)}]]]

         [:.separator {:background (:bg-medium-border colors)
                       :width      "1px"
                       :height     "16px"
                       :margin     "0 6px"}]

         [:.input {:color       (:text-normal colors)
                   :outline     "0"
                   :margin      "0 2px"
                   :font-family label-font-family
                   :font-size   label-font-size
                   :padding     "2px 4px"}]]}

  (let [css (css/get-classnames ToolBar)]
    (dom/div (h/props+classes this {:className (:container css)})
      (fp/children this))))

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

(fp/defsc AutoFocusInput
  [this props]
  {:componentDidMount #(.select (dom/node this))}
  (dom/input props))

(def auto-focus-input (fp/factory AutoFocusInput))

(fp/defsc InlineEditor
  [this {::keys [editing? editor-value]} {::keys [value on-change] :as computed} css]
  {:initial-state (fn [_]
                    {::editor-id    (random-uuid)
                     ::editing?     false
                     ::editor-value ""})
   :ident         [::editor-id ::editor-id]
   :query         [::editor-id ::editing? ::editor-value]
   :css           [[:.container {:flex 1}]
                   [:.no-label {:font-style "italic"
                                :color      (:text-faded colors)}]
                   [:.label {:color       (:text-strong colors)
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
                    [:.flex {:flex "1"}]
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
  (include-children [_] [ToolBar Row InlineEditor]))

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
