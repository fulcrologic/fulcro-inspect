(ns fulcro.inspect.ui.settings
  (:require
    [cljs.reader :refer [read-string]]
    [com.fulcrologic.fulcro-css.localized-dom :as dom]
    [com.fulcrologic.fulcro.components :as fp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.dom.events :as evt]
    [com.fulcrologic.fulcro.mutations :refer [defmutation]]
    [fulcro.inspect.ui.core :as ui]))

(def themes
  "The values of the `:setting/theme` setting, in the order the settings panel lists them, with their labels.
   No saved value means `:setting.theme/light`."
  [[:setting.theme/light "Light"]
   [:setting.theme/dark "Dark"]
   [:setting.theme/system "Same as the operating system"]])

(defonce ^:private theme-setting* (atom :setting.theme/light))

(def ^:private dark-scheme-query "(prefers-color-scheme: dark)")

(defn- dark-theme?
  "Returns true when the `theme` setting resolves to the dark theme, asking the browser about the operating system
   color scheme for `:setting.theme/system`."
  [theme]
  (case theme
    :setting.theme/dark true
    :setting.theme/system (.-matches (js/window.matchMedia dark-scheme-query))
    false))

(defn apply-theme!
  "Shows the inspector in the theme that the `theme` setting names, by setting the `data-theme` attribute of the
   `html` element to `dark` or `light`. A nil `theme` means `:setting.theme/light`."
  [theme]
  (let [theme (or theme :setting.theme/light)]
    (reset! theme-setting* theme)
    (.setAttribute js/document.documentElement "data-theme" (if (dark-theme? theme) "dark" "light"))))

(defonce ^:private follow-system-scheme
  ;; Applies the theme again when the operating system scheme changes, which matters only for :setting.theme/system.
  (.addEventListener (js/window.matchMedia dark-scheme-query) "change"
    (fn [_] (apply-theme! @theme-setting*))))

(defmutation update-settings [params]
  (action [{:keys [state] :as env}]
    (swap! state update :fulcro.inspect/settings merge params)))

(defmutation save-settings [params]
  (remote [env]
    (-> env :ast (assoc :key 'save-settings)))
  (action [{:keys [state] :as env}]
    (swap! state update :fulcro.inspect/settings merge params)))

(defsc SettingsQuery [_ _]
  {:query [:setting/websocket-port :setting/compact-keywords? :setting/theme]})

(defn load-settings [app]
  (df/load app :fulcro.inspect/settings SettingsQuery))

(defsc Settings [this {:keys    [fulcro.inspect/settings]
                       :ui/keys [hide-websocket?]}
                 {:keys [close-settings!]}]
  {:ident             (fn [] [:component/id ::Settings])
   :query             [:ui/hide-websocket?
                       {[:fulcro.inspect/settings '_]
                        [:setting/websocket-port :setting/compact-keywords? :setting/theme]}]
   :componentDidMount (fn [this] (load-settings this))
   :initial-state     {}
   :css               [[:.container {:padding "12px"}]]}
  (let [{:setting/keys [websocket-port compact-keywords? theme]} settings]
    (dom/div
      (when close-settings!
        (ui/toolbar {:classes [:.details]}
          (ui/toolbar-spacer)
          (ui/toolbar-action {:onClick close-settings!}
            (ui/icon {:title "Close panel"} :clear))))
      (dom/div :.container
        (ui/header {} "Settings")
        (dom/div :$margin-left-standard
          (if-not hide-websocket?
            (ui/row {:classes [:.align-center]}
              (ui/label {} "Websocket Port:")
              (ui/input {:value    (or websocket-port 0)
                         :type     "number"
                         :onChange #(fp/transact! this [(update-settings {:setting/websocket-port (js/parseInt (evt/target-value %))})])})
              (ui/primary-button {:onClick #(fp/transact! this [(save-settings {:setting/websocket-port websocket-port})])}
                "Restart Websockets")))
          (ui/row {:classes [:.align-center]}
            (ui/label {}
              (dom/input :$margin-right-small
                {:checked  (or compact-keywords? false)
                 :type     "checkbox"
                 :onChange #(fp/transact! this [(save-settings {:setting/compact-keywords? (not compact-keywords?)})])})
              "Compact Keywords in DB Explorer?"))
          (ui/row {:classes [:.align-center]}
            (ui/label {}
              (dom/span :$margin-right-small "Theme:")
              (dom/select {:value    (pr-str (or theme :setting.theme/light))
                           :onChange #(fp/transact! this [(save-settings {:setting/theme (read-string (evt/target-value %))})])}
                (for [[value label] themes]
                  (dom/option {:key value :value (pr-str value)} label))))))))))

(def ui-settings (fp/computed-factory Settings))
