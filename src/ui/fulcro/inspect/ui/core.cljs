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

(def light-theme-colors
  "Every color the inspector UI uses, keyed by the role it plays, with its value in the light theme. Components
   refer to a color through `color`, never by value, so that a theme can restyle the UI by redefining the CSS
   custom properties that `css-theme-variables` declares."
  {;; backgrounds
   :bg                     "#fff"
   :bg-alt                 "#fafafa"
   :bg-subtle              "#f5f5f5"
   :bg-toolbar             "#f3f3f3"
   :bg-code                "#f6f7f8"
   :bg-muted               "#eee"
   :bg-tab-hover           "#e5e5e5"
   :bg-zoom-hover          "#e0e0e0"
   :bg-list-index          "#dddddd"
   :bg-row-hover           "#eef3fa"
   :bg-row-selected        "#e6e6e6"
   :bg-notice              "#fff4c5"
   :bg-highlight           "yellow"
   :bg-display-name        "#e5efff"
   :bg-badge               "#777"
   :bg-tx-count            "#6D84AF"
   :bg-toggler-hover       "#c2c2c2"
   :bg-toggler-pressed     "#797979"
   :bg-toggler-active      "#aaa"
   :bg-diff-added          "rgba(100, 255, 100, 0.08)"
   :bg-index-state         "#000"
   :index-ready            "#36c74b"
   :index-loading          "#efe43b"
   :index-unavailable      "#848484"
   ;; accents and controls
   :accent                 "#1973e7"
   :accent-border          "#2b7ce8"
   :accent-hover           "#3a86e8"
   :accent-disabled        "#a7c5f1"
   :accent-disabled-border "#a3c2f1"
   :button-text            "#1873E8"
   :button-text-disabled   "#adcbf7"
   :tab-selected           "#5c7ebb"
   :icon-accent            "#4682E9"
   :link                   "#4183c4"
   :link-alt               "#6ea6e4"
   :text-on-accent         "#fff"
   :text-shadow-on-accent  "#1b1b1b"
   :oge-button             "#0275d8"
   :oge-button-hover       "#025aa5"
   :oge-button-hover-border "#01549b"
   :oge-input-text         "#464a4c"
   :oge-input-border       "rgba(0,0,0,.15)"
   :oge-input-focus        "#5cb3fd"
   :success                "#5cb85c"
   :warning                "#f0ad4e"
   ;; text and icons
   :text-max               "#000"
   :text-dark              "#222"
   :text-strong            "#333"
   :text-normal            "#5a5a5a"
   :text-faded             "#bbb"
   :text-muted             "#666"
   :text-timestamp         "#808080"
   :text-triangle          "#8f8f8f"
   :text-separator         "#b8b8b8"
   :text-label             "#434C54"
   :text-panel-header      "#424242"
   :text-display-name      "#051d38"
   :text-table             "#313942"
   :text-namespace         "grey"
   :text-error             "#F55"
   :text-error-strong      "#e80000"
   :icon-normal            "#6e6e6e"
   :icon-strong            "#333"
   ;; data syntax
   :syntax-nil             "#808080"
   :syntax-string          "#c41a16"
   :syntax-keyword         "#881391"
   :syntax-symbol          "#134f91"
   :syntax-number          "#1c00cf"
   :syntax-boolean         "#009999"
   :syntax-eql-composite   "#ab890d"
   :syntax-eql-ident       "#219"
   ;; borders
   :border                 "#ccc"
   :border-tab             "#C8C8C8"
   :border-light           "#e0e0e0"
   :border-statechart      "#ddd"
   :border-faint           "#eee"
   :border-watcher         "#efeef1"
   :border-table           "#d3d3d3"
   :border-toolbar         "#dadada"
   :border-input           "#c7c7c7"
   :border-panel           "#a3a3a3"
   :border-bg-light        "#e1e1e1"
   :border-bg-medium       "#cdcdcd"
   :border-diff            "rgba(60, 90, 60, 0.1)"
   :border-list-index      "rgba(100, 100, 100, 0.2)"
   ;; shadows
   :shadow-strong          "rgba(0, 0, 0, 0.26)"
   :shadow-soft            "rgba(0, 0, 0, 0.19)"
   :shadow-input           "rgba(0, 0, 0, 0.078)"
   :shadow-copied          "#00000038"})

(def dark-theme-colors
  "The value of every color role of `light-theme-colors` in the dark theme. It must have the same keys: a role
   missing here keeps its light value in the dark theme."
  {;; backgrounds
   :bg                     "#202124"
   :bg-alt                 "#242528"
   :bg-subtle              "#27282b"
   :bg-toolbar             "#292a2d"
   :bg-code                "#1c1d20"
   :bg-muted               "#35363a"
   :bg-tab-hover           "#3a3b3f"
   :bg-zoom-hover          "#3a3b3f"
   :bg-list-index          "#3a3b3f"
   :bg-row-hover           "#28344a"
   :bg-row-selected        "#3a3b3f"
   :bg-notice              "#4d4320"
   :bg-highlight           "#7a6a00"
   :bg-display-name        "#1d3557"
   :bg-badge               "#5f6368"
   :bg-tx-count            "#4a5f87"
   :bg-toggler-hover       "#45464a"
   :bg-toggler-pressed     "#6a6b6f"
   :bg-toggler-active      "#55565a"
   :bg-diff-added          "rgba(100, 255, 100, 0.04)"
   :bg-index-state         "#9aa0a6"
   :index-ready            "#36c74b"
   :index-loading          "#efe43b"
   :index-unavailable      "#848484"
   ;; accents and controls
   :accent                 "#3a7be0"
   :accent-border          "#4a87e6"
   :accent-hover           "#5591ea"
   :accent-disabled        "#2c4a75"
   :accent-disabled-border "#33527f"
   :button-text            "#8ab4f8"
   :button-text-disabled   "#4a5f80"
   :tab-selected           "#8ab4f8"
   :icon-accent            "#8ab4f8"
   :link                   "#8ab4f8"
   :link-alt               "#a8c7fa"
   :text-on-accent         "#fff"
   :text-shadow-on-accent  "#000"
   :oge-button             "#2b74db"
   :oge-button-hover       "#1f5fb8"
   :oge-button-hover-border "#1a54a3"
   :oge-input-text         "#d4d7db"
   :oge-input-border       "rgba(255,255,255,.2)"
   :oge-input-focus        "#5cb3fd"
   :success                "#5cb85c"
   :warning                "#f0ad4e"
   ;; text and icons
   :text-max               "#fff"
   :text-dark              "#e8eaed"
   :text-strong            "#dadce0"
   :text-normal            "#bdc1c6"
   :text-faded             "#6f7378"
   :text-muted             "#9aa0a6"
   :text-timestamp         "#9aa0a6"
   :text-triangle          "#9aa0a6"
   :text-separator         "#5f6368"
   :text-label             "#c4cad1"
   :text-panel-header      "#d0d3d7"
   :text-display-name      "#d2e3fc"
   :text-table             "#d0d4d9"
   :text-namespace         "#9aa0a6"
   :text-error             "#ff6b6b"
   :text-error-strong      "#ff5252"
   :icon-normal            "#9aa0a6"
   :icon-strong            "#dadce0"
   ;; data syntax
   :syntax-nil             "#9aa0a6"
   :syntax-string          "#f28b82"
   :syntax-keyword         "#d59bf6"
   :syntax-symbol          "#8ab4f8"
   :syntax-number          "#99a6ff"
   :syntax-boolean         "#5fd3c9"
   :syntax-eql-composite   "#e0c050"
   :syntax-eql-ident       "#8fa6ff"
   ;; borders
   :border                 "#4a4c50"
   :border-tab             "#4a4c50"
   :border-light           "#3c3d41"
   :border-statechart      "#45464a"
   :border-faint           "#323337"
   :border-watcher         "#333438"
   :border-table           "#45464a"
   :border-toolbar         "#3c3d41"
   :border-input           "#5f6368"
   :border-panel           "#5f6368"
   :border-bg-light        "#3c3d41"
   :border-bg-medium       "#4a4c50"
   :border-diff            "rgba(120, 200, 120, 0.2)"
   :border-list-index      "rgba(200, 200, 200, 0.2)"
   ;; shadows
   :shadow-strong          "rgba(0, 0, 0, 0.6)"
   :shadow-soft            "rgba(0, 0, 0, 0.5)"
   :shadow-input           "rgba(0, 0, 0, 0.3)"
   :shadow-copied          "#00000080"})

(defn color
  "Returns a CSS `var()` reference to the theme color for `role`, a key of `light-theme-colors`. Throws on an
   unknown `role`, because the browser would otherwise drop the declaration without an error."
  [role]
  (when-not (contains? light-theme-colors role)
    (throw (ex-info (str "Unknown theme color " role) {:role role})))
  (str "var(--fi-" (name role) ")"))

(defn- theme-variables
  "Returns a garden property map that declares each color of `theme-colors` as a `--fi-` CSS custom property."
  [theme-colors]
  (into {} (map (fn [[role value]] [(str "--fi-" (name role)) value])) theme-colors))

(def css-theme-variables
  "A garden rule that declares every theme color as a CSS custom property on the document root."
  [":root" (theme-variables light-theme-colors)])

(def dark-theme-selector
  "The CSS selector that matches the document root while the dark theme is on. `apply-theme!` turns the theme on
   by setting the `data-theme` attribute of the `html` element."
  ":root[data-theme=\"dark\"]")

(def css-dark-theme
  "Garden rules for the dark theme: the dark values of the theme colors, plus overrides for the CodeMirror and
   flame graph stylesheets, which the shells load from static CSS files that do not use the theme colors."
  (let [dark (fn [selector] (str dark-theme-selector " " selector))]
    (list
      [dark-theme-selector (assoc (theme-variables dark-theme-colors) :color-scheme "dark")]
      [(dark "body") {:background (color :bg) :color (color :text-normal)}]
      ;; `fulcro-icon` SVGs have no class, so the default fill (black) applies to them
      [(dark "svg[role=\"img\"]") {:fill (color :icon-strong)}]
      [(dark ".CodeMirror") {:background (color :bg-code) :color (color :text-strong)}]
      [(dark ".CodeMirror-gutters") {:background   (color :bg-toolbar)
                                     :border-right (str "1px solid " (color :border))}]
      [(dark ".CodeMirror-scrollbar-filler") {:background (color :bg)}]
      [(dark ".CodeMirror-gutter-filler") {:background (color :bg)}]
      [(dark ".CodeMirror-linenumber") {:color (color :text-muted)}]
      [(dark ".CodeMirror-guttermarker") {:color (color :text-strong)}]
      [(dark ".CodeMirror-cursor") {:border-left (str "1px solid " (color :text-max))}]
      [(dark ".CodeMirror-selected") {:background (color :bg-muted)}]
      [(dark ".CodeMirror-focused .CodeMirror-selected") {:background (color :bg-display-name)}]
      [(dark ".CodeMirror-activeline-background") {:background (color :bg-row-hover)}]
      [(dark ".CodeMirror-hints") {:background (color :bg-alt) :border (str "1px solid " (color :border))}]
      [(dark ".CodeMirror-hint") {:color (color :text-strong)}]
      [(dark ".cm-s-default .cm-keyword") {:color (color :syntax-keyword)}]
      [(dark ".cm-s-default .cm-atom") {:color (color :syntax-eql-ident)}]
      [(dark ".cm-s-default .cm-number") {:color (color :syntax-number)}]
      [(dark ".cm-s-default .cm-def") {:color (color :link)}]
      [(dark ".cm-s-default .cm-string") {:color (color :syntax-string)}]
      [(dark ".cm-s-default .cm-comment") {:color (color :text-muted)}]
      [(dark ".cm-s-default .cm-bracket") {:color (color :text-muted)}]
      [(dark ".cm-s-default .cm-builtin") {:color (color :syntax-keyword)}]
      [(dark ".d3-flame-graph rect") {:stroke (color :bg)}])))

(def color-bg-light (color :bg-subtle))
(def color-bg-light-border (color :border-bg-light))
(def color-bg-medium-border (color :border-bg-medium))

(def color-text-normal (color :text-normal))
(def color-text-strong (color :text-strong))
(def color-text-faded (color :text-faded))

(def color-icon-normal (color :icon-normal))
(def color-icon-strong (color :icon-strong))

(def color-row-hover (color :bg-row-hover))
(def color-row-selected (color :bg-row-selected))

(def box-shadow (str "0 6px 6px " (color :shadow-strong) ", 0 9px 20px " (color :shadow-soft)))

(def css-label-font
  {:font-family label-font-family
   :font-size   label-font-size})

(def css-info-group
  {:border-top (str "1px solid " (color :border-faint))
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
   :color       (color :text-timestamp)
   :margin      "0 4px 0 7px"})

(def css-flex-column
  {:flex           "1"
   :display        "flex"
   :flex-direction "column"})

(def css-triangle
  {:font-family    label-font-family
   :font-size      label-font-size
   :color          (color :text-triangle)
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
   [:&:hover {:border (str "1px solid " (color :border-light))}]
   [:&:focus {:border (str "1px solid " (color :accent))}]])

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
                 [:.separator {:color       (color :text-separator)
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
          {:border  (str "1px solid " (color :border-table))
           :padding "3px 5px"}]]}
  (dom/td :.container props (fc/children this)))

(def td (fc/factory TableCell))

(fc/defsc TableHead
  [this props]
  {:css [[:.container css-label-font
          {:background  (color :bg-toolbar)
           :border      (str "1px solid " (color :border-table))
           :color       (color :text-max)
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
            {:background (color :bg-subtle)}]]]]}
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
  {:css         [[:.container {:border          (str "1px solid " (color :border-table))
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
          {:background    (color :bg)
           :border        (str "1px solid " (color :border))
           :cursor        "pointer"
           :margin        "0"
           :color         (color :button-text)
           :border-radius "4px"
           :padding       "6px 12px"
           :font-size     "12px"}
          [:&:hover {:background (color :bg-toolbar)}]
          [:&:disabled {:cursor "default"
                        :color  (color :button-text-disabled)}
           [:&:hover {:background (color :bg)}]]

          [:&.primary {:background   (color :accent)
                       :border-color (color :accent-border)
                       :color        (color :text-on-accent)}
           [:&:hover {:background (color :accent-hover)}]
           [:&:disabled {:background   (color :accent-disabled)
                         :border-color (color :accent-disabled-border)}]]]]}
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
  {:css [[:.label {:color        (color :text-label)
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
                    :border-bottom  (str "1px solid " (color :border-faint))}]]}
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
          [:&:hover {:background  (color :bg-toggler-hover)
                     :color       (color :text-on-accent)
                     :text-shadow (str "0 1px " (color :text-shadow-on-accent))}]
          [:&:active {:background  (color :bg-toggler-pressed)
                      :color       (color :text-on-accent)
                      :text-shadow (str "0 1px " (color :text-shadow-on-accent))}]
          [:&.active {:background  (color :bg-toggler-active)
                      :color       (color :text-on-accent)
                      :text-shadow (str "0 1px " (color :text-shadow-on-accent))}]]]}
  (dom/div :.toggler props (fc/children this)))

(def toggler (fc/factory Toggler))

(fc/defsc ToolBar [this _]
  {:css (fn []
          [[:.container {:border-bottom (str "1px solid " (color :border-toolbar))
                         :display       "flex"
                         :align-items   "center"}
            [:$c-icon {:fill      color-icon-normal
                       :transform "scale(0.7)"}
             [:&:hover {:fill color-icon-strong}]]

            [:&.details {:background    (color :bg-toolbar)
                         :border-bottom (str "1px solid " (color :border))
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

           [:.separator {:background (color :border)
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
                   [:.input {:border     (str "1px solid " (color :border-input))
                             :box-shadow (str "0px 1px 3px 1px " (color :shadow-input))
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
  {:css         [css-theme-variables
                 css-dark-theme
                 [:.focused-panel {:border-top     (str "1px solid " (color :border-panel))
                                   :display        "flex"
                                   :flex-direction "column"
                                   :height         "50%"}]
                 [:.focused-container css-flex-column {:overflow "auto"
                                                       :padding  "0 10px"}]

                 [:a {:color           (color :link)
                      :text-decoration "none"}]

                 [:.info-group css-info-group
                  [(gs/& gs/first-child) {:border-top "0"}]]
                 [:.info-label css-info-label]
                 [:.flex {:flex "1"}]
                 [:$flex {:flex "1"}]
                 [:$flex-100 {:flex "1" :max-width "100%"}]
                 [:$highlight {:background (color :bg-highlight)}]
                 [:.ident {:padding     "5px 6px"
                           :background  (color :bg-toolbar)
                           :color       (color :text-panel-header)
                           :display     "inline-block"
                           :font-family mono-font-family
                           :font-size   label-font-size}]
                 [:.display-name {:background  (color :bg-display-name)
                                  :color       (color :text-display-name)
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
