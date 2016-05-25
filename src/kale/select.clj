;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.select
  (:require [kale.common :refer [fail new-line unknown-action
                                get-options reject-extra-args
                                get-command-msg]]
            [kale.update :refer [update-user-selection
                                update-org-space]]
            [kale.cloud-foundry :as cf]
            [kale.aliases :as aliases]
            [clojure.string :as str]
            [cheshire.core :as json]
            [kale.getter :as my]
            [kale.retrieve-and-rank :as rnr]))

(defn get-msg
  "Return the corresponding select message"
   [msg-key & args]
   (apply get-command-msg :select-messages msg-key args))

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
    (fail (get-msg :missing-org-name)))
  (let [cf-auth (cf/generate-auth state)
        orgs (cf/get-organizations cf-auth)
        org (cf/find-entity orgs org-name)]
    (when (nil? org)
      (fail (get-msg :unknown-org org-name)))
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
      (str (get-msg :switch-org org-name space-name)
           (cond
             (> space-count 8)
               (get-msg :other-spaces-num (dec space-count))
             (> space-count 1)
               (get-msg :other-spaces (list-spaces spaces space-name)))))))

(defmethod select :space
  [state [cmd what space-name & args] flags]
  (reject-extra-args args cmd what)
  (get-options flags {})
  (when (empty? space-name)
    (fail (get-msg :missing-space-name)))
  (let [cf-auth (cf/generate-auth state)
        org-guid (-> state :org-space :guid :org)
        spaces (cf/get-spaces cf-auth org-guid)
        space (cf/find-entity spaces space-name)]
    (when (nil? space)
      (fail (get-msg :unknown-space space-name)))
    (do
      (update-org-space (-> state :org-space :org)
                        org-guid
                        space-name
                        (-> space :metadata :guid)
                        state)
      (get-msg :switch-space space-name))))

(defn service-selector
  "Select a service based on its type."
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
      (fail (get-msg :unknown-service service-name)))
    (when (and named-service-type (not= my-type-name named-service-type))
      (fail (get-msg :wrong-service-type
                     service-name named-service-type my-type-name)))
    (when-not my-service-name
      (fail (get-msg :unclear-default-service my-type-name)))
    (update-user-selection state my-type-key my-service-name)
    (get-msg :service-selected (name my-service-name) my-type-name)))

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
    (fail (get-msg :missing-convert-filename)))
  (let [contents (try (slurp filename)
                      (catch Exception e
                        (fail (get-msg :cant-read-file filename))))]
    (try (json/decode contents)
         (catch Exception e
           (fail (get-msg :invalid-json filename)))))
  (update-user-selection state :conversion-configuration-file filename)
  (get-msg :convert-file-selected filename))

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
      (fail (get-msg :unclear-base-rnr)))
    (when (empty? clusters)
      (fail (get-msg :no-clusters (name service-key))))
    (when (and (some? cluster-name) (empty? found-clusters))
      (fail (get-msg :unknown-cluster
                     cluster-name (name service-key)
                     (clusters-to-string clusters))))
    (when (< 1 (count found-clusters))
      (if (nil? cluster-name)
          (fail (get-msg :unclear-default-cluster
                         (clusters-to-string clusters)))
          (fail (get-msg :multiple-clusters
                         (count found-clusters) cluster-name))))
    (update-user-selection state :cluster (merge
                                            {:service-key (name service-key)}
                                            my-cluster))
    (get-msg :cluster-selected (:cluster_name my-cluster))))

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
      (fail (get-msg :unclear-base-cluster)))
    (when (empty? solr-configs)
      (fail (get-msg :no-configs cluster_name)))
    (when (and (some? config-name) (empty? found-configs))
      (fail (get-msg :unknown-config
                     config-name (name service-key) cluster_name
                     solr-configs)))
    (when (and (nil? config-name) (< 1 (count found-configs)))
        (fail (get-msg :unclear-default-config solr-configs)))
    (update-user-selection state :config
                                 {:service-key service-key
                                  :cluster-id solr_cluster_id
                                  :config-name my-config})
    (get-msg :config-selected my-config)))

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
      (fail (get-msg :unclear-base-cluster)))
    (when (empty? collections)
      (fail (get-msg :no-collections (name service-key) cluster_name)))
    (when (and (some? collection-name) (empty? found-collections))
      (fail (get-msg :unknown-collection
                     collection-name (name service-key) cluster_name
                     collections)))
    (when (and (nil? collection-name) (< 1 (count found-collections)))
        (fail (get-msg :unclear-default-collection collections)))
    (update-user-selection state :collection
                                 {:service-key service-key
                                  :cluster-id solr_cluster_id
                                  :cluster-name cluster_name
                                  :collection-name my-collection})
    (get-msg :collection-selected my-collection)))
