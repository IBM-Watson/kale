;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.persistence
  (:require [cheshire.core :as json]))

(def state-filename "kale-state.json")

(defn read-state
  "Read our current configuration from disk.
   If the file cannot be read, silently default to an empty configuration.
   If the configuration cannot be read as JSON, throw an exception.
   This needs to be more robust."
  ([] (read-state state-filename))
  ([filename]
   (json/decode (try (slurp filename)
                       (catch Exception ex "{}"))
                  true)))

(defn write-state
  "Writes configuration to disk."
  ([state] (write-state state state-filename))
  ([state filename]
   (let [state-str (json/encode state {:pretty true})]
     (spit filename state-str))))
