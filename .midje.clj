;; make sure emitter is set only in CircleCI environment
(when (System/getenv "CIRCLE_ARTIFACTS")
  (change-defaults :emitter 'midje.emission.plugins.junit
                   :print-level :print-facts
                   :colorize false))
