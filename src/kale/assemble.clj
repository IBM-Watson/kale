;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.assemble
  (:require [kale.create :refer [create]]
            [kale.select :refer [select]]
            [kale.delete :refer [delete]]
            [kale.aliases :as aliases]
            [kale.common :refer [fail readable-files? new-line
                                get-options reject-extra-args
                                unknown-action get-command-msg
                                try-function]]
            [kale.persistence :as persist]
            [kale.retrieve-and-rank :as rnr]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn get-msg
  "Return the corresponding create message"
   [msg-key & args]
   (apply get-command-msg :assemble-messages msg-key args))

(defn wizard-command
  "Run the specified command and rollback if it fails"
  [state command args flags rollback]
  (println (get-msg :running-cmd
                    (str/join " " args)
                    (str (when (seq flags) " ")
                         (str/join " " flags))))
  (println (try-function command [state args flags] rollback)))

(defn run-wizard
  "Run a list of commands and rollback if any of the commands fail"
  [cmd-list rollback]
  (doseq [cmd cmd-list]
    (let [state (persist/read-state)]
      (apply wizard-command (concat [state] cmd [rollback])))))

(def assemble-options {
  :enterprise aliases/enterprise-option})

(defn assemble
  "Run the series of commands for creating the two services and
  Solr collection"
  [state [cmd base-name config-name config-zip & args] flags]
  (reject-extra-args args cmd)
  (let [options (get-options flags assemble-options)
        starting-space (-> state :org-space :space)]
    (when-not base-name
      (fail (get-msg :missing-base-name)))
    (when-not config-name
      (fail (get-msg :missing-config-name)))
    (rnr/validate-solr-name base-name)
    (rnr/validate-solr-name config-name)
    ;; Check if there will be any errors when getting the config zip
    (if config-zip
      (do (readable-files? [config-zip])
          (io/file config-zip))
      (let [resource (io/resource (str config-name ".zip"))]
        (when-not resource
          (fail (get-msg :unknown-packaged-config config-name)))
          (io/input-stream resource)))

    (when (some? (options :enterprise))
      (println (get-msg :no-rnr-enterprise)))
    ;; Create the space to put the instance in
    (wizard-command
      state create ["create" "space" base-name] []
      (fn [] (fail (get-msg :failure base-name))))
    ;; Create the individual components in the newly made space
    (run-wizard
      [[create ["create" "document_conversion" (str base-name "-dc")]
               (if (some? (options :enterprise)) ["--enterprise"] [])]
       [create ["create" "retrieve_and_rank" (str base-name "-rnr")] []]
       [create ["create" "cluster" (str base-name "-cluster")] ["--wait"]]
       [create (concat ["create" "solr-configuration" config-name]
                       (if (some? config-zip) [config-zip] [])) []]
       [create ["create" "collection" (str base-name "-collection")] []]]
      ;; Rollback
      (fn [] (println (str new-line (get-msg :starting-rollback)))
             (run-wizard [[select ["select" "space" starting-space] []]
                          [delete ["delete" "space" base-name] ["--y"]]]
                         (fn [] nil))
             (fail (get-msg :failure base-name))))
    (get-msg :success base-name)))
