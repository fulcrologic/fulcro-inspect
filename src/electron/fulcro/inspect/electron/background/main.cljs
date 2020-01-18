(ns fulcro.inspect.electron.background.main
  (:require
    ["electron" :as electron]
    ["path" :as path]
    ["electron-settings" :as settings]
    ["url" :as url]
    [goog.functions :as g.fns]
    [fulcro.inspect.electron.background.server :as server]))

(defn get-setting [k default] (or (.get settings k) default))
(defn set-setting! [k v] (.set settings k v))

(defn save-state! [window]
  (mapv (fn [[k v]] (set-setting! (str "BrowserWindow/" k) v))
    (js->clj (.getBounds window))))

(defn debounce [ms f] (g.fns/debounce f ms))

(defn create-window []
  (let [win (electron/BrowserWindow.
              #js {:width          (get-setting "BrowserWindow/width" 800)
                   :height         (get-setting "BrowserWindow/height" 600)
                   :x              (get-setting "BrowserWindow/x" js/undefined)
                   :y              (get-setting "BrowserWindow/y" js/undefined)
                   :webPreferences #js {:nodeIntegration true}})]
    (.loadURL win (url/format #js {:pathname (path/join js/__dirname ".." ".." "index.html")
                                   :protocol "file:"
                                   :slashes  "true"}))
    (.. win -webContents openDevTools)
    (doto win
      (.on "resize" (debounce 500 #(save-state! win)))
      (.on "move" (debounce 500 #(save-state! win)))
      (.on "close" (debounce 500 #(save-state! win))))
    (server/start! (.-webContents win))))

(defn init []
  (js/console.log "init")
  (electron/app.on "ready" create-window))

(defn done []
  (js/console.log "Done reloading"))

