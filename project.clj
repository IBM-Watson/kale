;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(defproject kale "1.3.0"
  :description "A command line tool for provisioning and configuring the Retrieve and Rank Service and the Document Conversion Service"
  :url "https://github.com/IBM-Watson/kale"
  :min-lein-version "2.5.0"
  :license {:name "MIT"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/data.codec "0.1.0"]
                 [cheshire "5.5.0"]
                 [clj-http "2.1.0"]
                 [slingshot "0.12.2"]
                 ;; Not a direct dependency.
                 ;; This is here to lock in a specific version.
                 [riddley "0.1.12"]]
  :main ^:skip-aot kale.main
  :target-path "target/%s"
  :prep-tasks [["exec" "solr/package.clj"]
               "compile"]
  :profiles {:dev {:dependencies [[clj-http-fake "1.0.2"]]
                   :plugins [[jonase/eastwood "0.2.3"
                              :exclusions [org.clojure/clojure]]
                             [lein-bikeshed "0.3.0"
                              :exclusions [org.clojure/clojure]]
                             [lein-cloverage "1.0.6"
                              :exclusions [org.clojure/clojure]]
                             [lein-exec "0.3.6"
                              :exclusions [org.clojure/clojure]]
                             [lein-kibit "0.1.2"
                              :exclusions [org.clojure/clojure]]
                             [lein-shell "0.4.1"
                              :exclusions [org.clojure/clojure]]]}
             :uberjar {:aot :all}})
