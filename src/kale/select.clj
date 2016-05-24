;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.select
  (:require [kale.common :refer [fail new-line unknown-action
                                get-options reject-extra-args]]
            [kale.update :refer [update-user-selection
                                update-org-space]]
            [kale.cloud-foundry :as cf]
            [kale.aliases :as aliases]
            [clojure.string :as str]
            [cheshire.core :as json]
            [kale.getter :as my]
            [kale.retrieve-and-rank :as rnr]))

(def select-items
  {:organization aliases/organization
   :space aliases/space
   :document-conversion aliases/document-conversion
   :conversion-configuration aliases/conversion-configuration
   :retrieve-and-rank aliases/retrieve-and-rank
   :cluster aliases/cluster
   :solr-configuration aliases/solr-configuration
   :collection aliases/collection})

(defmulti select (fn [_ [_ what & _] _]
                   (or (some (fn [[k a]] (when (a what) k)) select-items)
                       :unknown)))

(defmethod select :unknown
  [state [cmd what & args] flags]
  (unknown-action what cmd ["organization" "space" "document_conversion"
                            "conversion-configuration"
                            "retrieve_and_rank" "cluster"
                            "solr-configuration" "collection"]))

(defn list-spaces
  [spaces current-space]
  (let [space-names (map (fn [s] (-> s :entity :name)) spaces)
        names-to-print (remove #(= % current-space) space-names)]
    (str/join " " names-to-print)))

(defmethod select :organization
  [state [cmd what org-name & args] flags]
  (reject-extra-args args cmd what)
  (get-options flags {})
  (when (empty? org-name)
    (fail "Please specify an org to change to."))
  (let [cf-auth (cf/generate-auth state)
        orgs (cf/get-organizations cf-auth)
        org (cf/find-entity orgs org-name)]
    (when (nil? org)
      (fail (str "Unable to locate org '" org-name "'.")))
    (let [org-guid (-> org :metadata :guid)
          spaces (cf/get-spaces cf-auth org-guid)
          space-count (count spaces)
          space (first spaces)
          space-name (-> space :entity :name)]
      (update-org-space org-name
                        org-guid
                        space-name
                        (-> space :metadata :guid)
                        state)
      (str "Switched to using org '" org-name "'." new-line
           "Switched to using space '" space-name "'." new-line
           (cond
             (> space-count 8)
               (str "There are " (dec space-count) " other spaces in this org.")
             (> space-count 1)
               (str "Other space(s) in this org include ["
                    (list-spaces spaces space-name) "]."))))))

(defmethod select :space
  [state [cmd what space-name & args] flags]
  (reject-extra-args args cmd what)
  (get-options flags {})
  (when (empty? space-name)
    (fail "Please specify a space to change to."))
  (let [cf-auth (cf/generate-auth state)
        org-guid (-> state :org-space :guid :org)
        spaces (cf/get-spaces cf-auth org-guid)
        space (cf/find-entity spaces space-name)]
    (when (nil? space)
      (fail (str "Unable to locate space '" space-name "'.")))
    (do
      (update-org-space (-> state :org-space :org)
                        org-guid
                        space-name
                        (-> space :metadata :guid)
                        state)
      (str "Switched to using space '" space-name "'."))))

(defn service-selector
  "Select a service based on it's type."
  [state my-type-key getter service-name]
  (let [my-type-name (name my-type-key)
        named-service-type (and service-name
                                (:type ((keyword service-name)
                                        (:services state))))
        default-service-name (first (getter state))
        my-service-name (if named-service-type
                     service-name
                     default-service-name)]
    (when (and service-name (not named-service-type))
      (fail (str "No service named '" service-name "' was found.")))
    (when (and named-service-type (not= my-type-name named-service-type))
      (fail (str "'" service-name "' is a " named-service-type
                 " service, not a " my-type-name " service.")))
    (when-not my-service-name
      (fail (str "Couldn't figure out a default " my-type-name
                 " service to use.")))
    (update-user-selection state my-type-key my-service-name)
    (str "You have selected '" (name my-service-name)
         "' as your current " my-type-name " service.")))

(defmethod select :document-conversion
  [state [cmd what service-name & args] flags]
  (reject-extra-args args cmd what)
  (get-options flags {})
  (service-selector state :document_conversion
                    my/conversion-service service-name))

(defmethod select :retrieve-and-rank
  [state [cmd what service-name & args] flags]
  (reject-extra-args args cmd what)
  (get-options flags {})
  (service-selector state :retrieve_and_rank my/rnr-service service-name))

(defmethod select :conversion-configuration
  [state [cmd what filename & args] flags]
  (reject-extra-args args cmd what)
  (get-options flags {})
  (when (nil? filename)
    (fail (str "Please give the name of a file that"
               " contains conversion configuration JSON.")))
  (let [contents (try (slurp filename)
                      (catch Exception e
                        (fail (str "Cannot read the file '" filename "'."))))]
    (try (json/decode contents)
         (catch Exception e
           (fail (str "The contents of '" filename "' is not JSON.")))))
  (update-user-selection state :conversion-configuration-file filename)
  (str "Conversion configuration is now set to '" filename "'."))

(defn clusters-to-string
  [clusters]
  (str/join " " (map :cluster_name clusters)))

(defmethod select :cluster
  [state [cmd what cluster-name & args] flags]
  (reject-extra-args args cmd what)
  (get-options flags {})
  (let [[service-key service-info] (my/rnr-service state)
        clusters (and service-info
                      (rnr/list-clusters (:credentials service-info)))
        found-clusters (filter #(or (not cluster-name)
                                    (= cluster-name (:cluster_name %)))
                               clusters)
        my-cluster (first found-clusters)]
    (when-not service-key
      (fail "Please select or create a retrieve_and_rank service."))
    (when (empty? clusters)
      (fail (str "No Solr clusters found in '" (name service-key) "'.")))
    (when (and (some? cluster-name) (empty? found-clusters))
      (fail (str "No Solr cluster named '" cluster-name
                 "' found in '" (name service-key) "'." new-line
                 "Available clusters: " (clusters-to-string clusters))))
    (when (< 1 (count found-clusters))
      (if (nil? cluster-name)
          (fail (str "Please select a cluster to use." new-line
                     "Available clusters: " (clusters-to-string clusters)))
          (fail (str "There are " (count found-clusters)
                     " with the name '" cluster-name "'."))))
    (update-user-selection state :cluster (merge
                                            {:service-key (name service-key)}
                                            my-cluster))
    (str "You have selected '" (:cluster_name my-cluster)
         "' as your current Solr cluster.")))

(defmethod select :solr-configuration
  [state [cmd what config-name & args] flags]
  (reject-extra-args args cmd what)
  (get-options flags {})
  (let [{:keys [solr_cluster_id cluster_name service-key]} (my/cluster state)
        solr-configs (and solr_cluster_id
                          (rnr/list-configs
                            (my/creds-for-service state (keyword service-key))
                            solr_cluster_id))
        found-configs (filter #(or (not config-name) (= config-name %))
                              solr-configs)
        my-config (first found-configs)]
    (when-not service-key
      (fail "Please select or create a retrieve_and_rank cluster."))
    (when (empty? solr-configs)
      (fail (str "No Solr configurations found in '" cluster_name "'.")))
    (when (and (some? config-name) (empty? found-configs))
      (fail (str "No Solr configurations named '" config-name
                 "' found in '" (name service-key) "/" cluster_name "'."
                 new-line "Available configurations: " solr-configs)))
    (when (and (nil? config-name) (< 1 (count found-configs)))
        (fail (str "Please select a Solr configuration to use." new-line
                   "Available configurations: " solr-configs)))
    (update-user-selection state :config
                                 {:service-key service-key
                                  :cluster-id solr_cluster_id
                                  :config-name my-config})
    (str "You have selected '" my-config
         "' as your current Solr configuration.")))

(defmethod select :collection
  [state [cmd what collection-name & args] flags]
  (reject-extra-args args cmd what)
  (get-options flags {})
  (let [{:keys [solr_cluster_id cluster_name service-key]} (my/cluster state)
        collections (and solr_cluster_id
                         (rnr/list-collections
                           (my/creds-for-service state (keyword service-key))
                           solr_cluster_id))
        found-collections (filter #(or (not collection-name)
                                   (= collection-name %)) collections)
        my-collection (first found-collections)]
    (when-not service-key
      (fail "Please select or create a retrieve_and_rank cluster."))
    (when (empty? collections)
      (fail (str "No Solr collections found in '" (name service-key) "/"
                 cluster_name "'.")))
    (when (and (some? collection-name) (empty? found-collections))
      (fail (str "No Solr collection named '" collection-name
                 "' found in '" (name service-key) "/" cluster_name "'."
                 new-line "Available collections: " collections)))
    (when (and (nil? collection-name) (< 1 (count found-collections)))
        (fail (str "Please select a collection to use." new-line
                   "Available collections: " collections)))
    (update-user-selection state :collection
                                 {:service-key service-key
                                  :cluster-id solr_cluster_id
                                  :cluster-name cluster_name
                                  :collection-name my-collection})
    (str "You have selected '" my-collection
         "' as your current Solr collection.")))
