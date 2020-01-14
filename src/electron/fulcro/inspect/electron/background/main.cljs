(ns fulcro.inspect.electron.background.main
  (:require
    [fulcro.inspect.electron.background.server :as server]
    ["electron" :as electron]
    ["path" :as path]
    ["url" :as url]))

(defonce contents (atom nil))

(defn create-window []
  (let [win (electron/BrowserWindow. #js {:width          800
                                          :height         600
                                          :webPreferences #js {:nodeIntegration true}})]
    (.loadURL win (url/format #js {:pathname (path/join js/__dirname ".." ".." "index.html")
                                   :protocol "file:"
                                   :slashes  "true"}))
    (.. win -webContents openDevTools)
    (reset! contents (.-webContents win))
    (server/start! {:content-atom contents})))

(defn init []
  (js/console.log "start")
  (electron/app.on "ready" create-window))

(defn done []
  (js/console.log "Done reloading"))

