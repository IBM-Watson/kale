;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.get-command
  (:require [kale.common :refer [fail get-options reject-extra-args
                                unknown-action new-line get-command-msg]]
            [kale.aliases :as aliases]
            [kale.retrieve-and-rank :as rnr]
            [kale.getter :as my]
            [slingshot.slingshot :refer [try+]]
            [clojure.java.io :as io]))

(defn get-msg
  "Return the corresponding get message"
   [msg-key & args]
   (apply get-command-msg :get-messages msg-key args))

(def get-items
  {:solr-configuration aliases/solr-configuration})

(defmulti get-command (fn [_ [_ what & _] _]
                        (or (some (fn [[k a]] (when (a what) k)) get-items)
                            :unknown)))

(defmethod get-command :unknown
  [state [cmd what & args] flags]
  (unknown-action what cmd ["solr-configuration"]))

(defmethod get-command :solr-configuration
  [state [cmd what user-config-name & args] flags]
  (reject-extra-args args cmd what)
  (get-options flags {})
  (let [cluster (my/cluster state)
        config-name (or user-config-name
                        (:config-name (my/solr-configuration state)))]
    (when-not config-name
      (fail (get-msg :missing-config)))
    (when-not cluster
      (fail (get-msg :missing-cluster)))
    (let [zip-contents (try+
                        (rnr/download-config
                         (my/creds-for-service state
                                               (keyword (:service-key cluster)))
                         (:solr_cluster_id cluster)
                         config-name)
                        (catch [:status 404] ex-data
                          (fail (get-msg :unknown-config config-name))))]
      (with-open [zip-file (io/output-stream
                            (io/file (str config-name ".zip")))]
        (.write zip-file zip-contents)))
    (str new-line (get-msg :saved-config config-name) new-line)))
