;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.getter
  (:require [cheshire.core :as json]
            [kale.common :refer [fail get-command-msg]]
            [kale.retrieve-and-rank :as rnr]))

;; Aimed for a side-effect free functional style, passing "state" in,
;; and not ever have the side effect of persisting state.

(defn get-msg
  "Return the corresponding getter message"
   [msg-key & args]
   (apply get-command-msg :getter-messages msg-key args))

(defn single-service
  "Look in our known services for services matching service-type. If
  there is exactly one, return the service name & details, else nil."
  [{:keys [services]} service-type]
  (let [found (filter
               (fn [[_ service]] (= (name service-type) (:type service)))
               services)]
    (when (= 1 (count found))
      (first found))))

(defn user-selection
  "Return the user selection for an item, if any."
  [{:keys [user-selections]} thing]
  (thing user-selections))

(defn selected-service
  "Get the fully fleshed out service info based on a key."
  [state service-type]
  (let [service-key (keyword (user-selection state service-type))]
    (and service-key
         [service-key (-> state :services service-key)])))

(defn index-document-version
  "Allow for the possibility of a user override for the
  document conversion service version string.
  This has a wired in default of \"2016-03-18\"."
  [state]
  (or (user-selection state :index-document-version)
      "2016-03-18"))

(defn conversion-configuration-file
  "Get the user's choice of a conversion configuration file."
  [state]
  (user-selection state :conversion-configuration-file))

(defn conversion-configuration
  "Read the conversion configuration from the user's
   choice of a conversion configuration file.
   Defaults to an empty configuration."
  [state]
  (let [file (conversion-configuration-file state)]
    (if-not file
      {}
      (let [raw (try (slurp file)
                     (catch Exception e
                       (fail (get-msg :cant-read-file file))))]
        (try (json/decode raw true)
             (catch Exception e
               (fail (get-msg :invalid-json file))))))))

(defn conversion-service
  "Return the one document conversion service the user wants to be
  working with. Returns nil is there is no service or are multiple
  services."
  [state]
  (or (selected-service state :document_conversion)
      (single-service state :document_conversion)))

(defn rnr-service
  "Return the one R&R service the user wants to be working with.
  Returns nil if there is no service or are multiple services."
  [state]
  (or (selected-service state :retrieve_and_rank)
      (single-service state :retrieve_and_rank)))

(defn creds-for-service
  [state service-key]
  (-> state :services service-key :credentials))

(defn cluster
  "Return the one cluster the user wants to be working with.
  Returns nil if there is no cluster or no service or if there are
  multiple services or multiple clusters."
  [state]
  (or (user-selection state :cluster)
      (let [[service-key service-details] (rnr-service state)
            clusters (and service-details
                          (rnr/list-clusters (:credentials service-details)))]
        (when (= 1 (count clusters))
          (merge {:service-key service-key}
                 (first clusters))))))

(defn collection
  "Return the one collection the user wants to be working with.
  Returns nil if there is no collection, no cluster or no service or
  if there are multiple services, multiple clusters or multiple
  collections."
  [state]
  (or (user-selection state :collection)
      (let [{:keys [solr_cluster_id cluster_name service-key]} (cluster state)
            collections (and solr_cluster_id
                             (rnr/list-collections
                              (creds-for-service state (keyword service-key))
                              solr_cluster_id))]
        (when (= 1 (count collections))
          {:service-key service-key
           :cluster-id solr_cluster_id
           :cluster-name cluster_name
           :collection-name (first collections)}))))

(defn solr-configuration
  "Return the one Solr configuration the user wants to be working with.
  Returns nil if there is no config, no cluster or no service or if
  there are multiple services, multiple clusters or multiple configs."
  [state]
  (or (user-selection state :config)
      (let [{:keys [solr_cluster_id cluster_name service-key]} (cluster state)
            configs (and solr_cluster_id
                             (rnr/list-configs
                              (creds-for-service state (keyword service-key))
                              solr_cluster_id))]
        (when (= 1 (count configs))
          {:service-key service-key
           :cluster-id solr_cluster_id
           :config-name (first configs)}))))
