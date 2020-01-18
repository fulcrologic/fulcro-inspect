(ns fulcro.inspect.electron.background.main
  (:require
    ["electron" :as electron]
    ["path" :as path]
    ["electron-settings" :as settings]
    ["url" :as url]
    [fulcro.inspect.electron.background.server :as server]))

(defn get-setting [k default] (or (.get settings k) default))
(defn set-setting! [k v] (.set settings k v))

(defn save-state! [window]
  (fn []
    (mapv (fn [[k v]] (set-setting! (str "window-" k) v))
      (js->clj (.getBounds window)))))

(defn create-window []
  (let [win (electron/BrowserWindow.
              #js {:width          (get-setting "window-width" 800)
                   :height         (get-setting "window-height" 600)
                   :x              (get-setting "window-x" js/undefined)
                   :y              (get-setting "window-y" js/undefined)
                   :webPreferences #js {:nodeIntegration true}})]
    (.loadURL win (url/format #js {:pathname (path/join js/__dirname ".." ".." "index.html")
                                   :protocol "file:"
                                   :slashes  "true"}))
    (.. win -webContents openDevTools)
    (doto win
      (.on "resize" (save-state! win))
      (.on "move" (save-state! win))
      (.on "close" (save-state! win)))
    (server/start! (.-webContents win))))

(defn init []
  (js/console.log "init")
  (electron/app.on "ready" create-window))

(defn done []
  (js/console.log "Done reloading"))

