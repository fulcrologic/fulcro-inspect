(ns fulcro.inspect.tool
  (:require
    [com.fulcrologic.fulcro.inspect.inspect-client
     :refer [app-uuid db-changed! ilet record-history-entry! remotes state-atom]]
    [com.fulcrologic.fulcro.inspect.tools :as tools]
    [fulcro.inspect.api.target-impl :refer [handle-inspect-event]]
    [taoensso.timbre :as log]))

(defonce apps* (atom {}))

(defn add-fulcro-inspect!
  "Adds Fulcro Inspect monitoring to your fulcro application.

   This function is a noop if Fulcro Inspect is disabled by compiler flags"
  [app]
  (ilet [id     (app-uuid app)
         state* (state-atom app)]
    (when-not (contains? apps* id)
      (log/info "Inspect initializing app" id)
      (swap! apps* assoc id app)
      (record-history-entry! app @state*)
      (tools/register-tool! app handle-inspect-event)
      (add-watch state* id #(db-changed! app %3 %4)))))
