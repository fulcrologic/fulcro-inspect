(ns fulcro.inspect.ci-test-main
  (:require fulcro.inspect.tests-to-run
            [doo.runner :refer-macros [doo-all-tests]]))

(doo-all-tests #"fulcro.inspect.*-spec")
