(ns fulcro.inspect.electron.background.main
  (:require ["electron" :as electron]
            ["path" :as path]
            ["url" :as url]))

(defn create-window []
  (let [win (electron/BrowserWindow. #js {:width 800 :height 600})]
    (.loadURL win (url/format #js {:pathname (path/join js/__dirname ".." ".." "index.html")
                               :protocol "file:"
                               :slashes  "true"}))

    (.. win -webContents openDevTools)))

(defn init []
  (electron/app.on "ready" create-window))

