;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.dry-run
  (:require [clojure.java.io :as io]
            [kale.common :refer [fail new-line readable-files?
                                 get-command-msg get-options
                                 new-line]]
            [kale.getter :as my]
            [kale.document-conversion :as dc]
            [clojure.string :as str]
            [cheshire.core :as json]))

(defn get-msg
  "Return the corresponding convert message"
   [msg-key & args]
   (apply get-command-msg :dry-run-messages msg-key args))

(defn get-dry-run-config
  "Get the configuration for conversion.
   Print a warning to the user if the configuration is empty.
   For failures, display a message to the user and throw an exception."
  [state]
  (let [json (my/conversion-configuration state)]
    (when (= {} json)
      (println (get-msg :empty-config)))
    (json/encode (update-in json
                            [:retrieve_and_rank :dry_run]
                            (fn [_] true)))))

(defn convert-one
  [config endpoint version in-file]
  (let [output-file (str "converted/" in-file ".json")]

    (print (get-msg :converting in-file))
    (flush)
    (io/make-parents output-file)
    (let [converted (try
                      (dc/convert endpoint version config (io/file in-file))
                      (catch Exception e
                        ;; Notice that we continue processing after this
                        (let [{:keys [error body]} (ex-data e)]
                          (println (str new-line
                                        (get-msg :conversion-failed in-file)
                                        new-line (or error body))))))]
      (when converted
        (spit output-file converted)
        (try (let [{:keys [warnings]} (json/decode converted true)]
               (if (empty? warnings)
                 (println (get-msg :completed))
                 (do (println (get-msg :warnings))
                     (println (json/encode warnings {:pretty true})))))
             (catch Exception e
                 (println (get-msg :invalid-json))))))))

(defn dry-run
  "The 'dry_run' command."
  [state [_ & filenames] flags]
  (get-options flags {})
  (if (readable-files? filenames)
    (let [config (get-dry-run-config state)
          endpoint (-> (my/conversion-service state) second :credentials)
          version (my/index-document-version state)]
      (doseq [in-file filenames]
        (convert-one config endpoint version in-file))
      (str new-line (get-msg :conversion-completed) new-line))))
