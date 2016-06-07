(ns kale.version)

;; This is a little weird. Leiningen gives us a Java System property
;; with our version number _at_compile_time_.

;; The unquote ~ here causes the getProperty to be evaluated at compile time.
(defmacro kale-version [] `~(System/getProperty "kale.version"))
