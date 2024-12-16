(ns fulcro.inspect.tool
  (:require
    [com.fulcrologic.fulcro.inspect.inspect-client
     :refer [app-uuid db-changed! ilet record-history-entry! remotes state-atom]]
    [com.fulcrologic.fulcro.inspect.tools :as tools]
    [com.fulcrologic.devtools.common.target :as ct]
    [com.fulcrologic.devtools.common.resolvers :as res]
    [com.fulcrologic.fulcro.inspect.target-impl :refer [handle-inspect-event]]
    [taoensso.timbre :as log]))

(defonce apps* (atom {}))
(defonce tool-connections (atom {}))

(defn add-fulcro-inspect!
  "Adds Fulcro Inspect monitoring to your fulcro application.

   This function is a noop if Fulcro Inspect is disabled by compiler flags"
  [app]
  (ilet [id (app-uuid app)
         state* (state-atom app)]
    (when-not (contains? apps* id)
      (let [c     (volatile! nil)
            tconn (ct/connect! {:target-id       id
                                :tool-type       :fulcro/inspect
                                :async-processor (fn [EQL]
                                                   (res/process-async-request {:fulcro/app         app
                                                                               :devtool/connection @c} EQL))
                                :description     (str (or id (:com.fulcrologic.fulcro.application/label app)))})]
        (log/info "Inspect initializing app" id)
        (vreset! c tconn)
        (swap! apps* assoc id app)
        (swap! tool-connections assoc id tconn)
        (record-history-entry! app @state*)
        (tools/register-tool! app (partial handle-inspect-event tconn))
        (add-watch state* id #(db-changed! app %3 %4))))))
