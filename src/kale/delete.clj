;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.delete
  (:require [kale.common :refer [fail new-line get-command-msg
                                 prompt-user-yn get-options
                                 reject-extra-args unknown-action]]
            [kale.aliases :as aliases]
            [kale.getter :as my]
            [kale.cloud-foundry :as cf]
            [kale.update :refer [delete-user-selection]]
            [kale.retrieve-and-rank :as rnr]
            [kale.persistence :as persist]))

(defn get-msg
  "Return the corresponding delete message"
  [msg-key & args]
  (apply get-command-msg :delete-messages msg-key args))

(def delete-items
  {:space aliases/space
   :retrieve-and-rank aliases/retrieve-and-rank
   :document-conversion aliases/document-conversion
   :cluster aliases/cluster
   :solr-configuration aliases/solr-configuration
   :collection aliases/collection})

(defmulti delete (fn [_ [_ what & _] _]
                   (or (some (fn [[k a]] (when (a what) k)) delete-items)
                       :unknown)))

(defmethod delete :unknown
  [state [cmd what & args] flags]
  (unknown-action what cmd ["space" "retrieve_and_rank"
                            "document_conversion" "cluster"
                            "solr-configuration" "collection"]))

(def delete-options
  {:yes aliases/yes-option})

(defmethod delete :space
  [state [cmd what space-name & args] flags]
  (reject-extra-args args cmd what)
  (let [options (get-options flags delete-options)]
    (when (empty? space-name)
      (fail (get-msg :missing-space-name)))
    (when (= space-name (-> state :org-space :space))
      (fail (get-msg :no-delete-current-space)))
    (let [cf-auth (cf/generate-auth state)
          org-guid (-> state :org-space :guid :org)
          spaces (cf/get-spaces cf-auth org-guid)
          space (cf/find-entity spaces space-name)
          space-guid (-> space :metadata :guid)]
      (when (nil? space)
        (fail (get-msg :unknown-space space-name)))

      ;; Check to see if the space contains services
      (let [services (cf/get-services cf-auth space-guid)
            num-services (count (keys services))]
        (when-not (zero? num-services)
          (println (get-msg :space-service-num num-services))
          (when-not (or (some? (options :yes))
                        (prompt-user-yn (get-msg :space-delete-confirm
                                                 space-name)))
            (fail (get-msg :delete-cancel)))))
      (cf/delete-space cf-auth space-guid)
      (str new-line (get-msg :space-deleted space-name) new-line))))

(defmethod delete :retrieve-and-rank
  [state [cmd what service-name & args] flags]
  (reject-extra-args args cmd what)
  (let [options (get-options flags delete-options)]
    (when (empty? service-name)
      (fail (get-msg :missing-rnr-name)))
    (let [kw (keyword service-name)
          {:keys [guid key-guid type credentials]} (-> state :services kw)
          cf-auth (cf/generate-auth state)]
      (when (nil? guid)
        (fail (get-msg :unknown-rnr service-name)))
      (when-not (= type "retrieve_and_rank")
        (fail (get-msg :not-rnr-service service-name)))

      ;; We need credentials to check if the service has clusters
      (when (some? key-guid)
        (let [num-clusters (count (rnr/list-clusters credentials))]
          (when-not (zero? num-clusters)
            (println (get-msg :rnr-cluster-num num-clusters))
            (when-not (or (some? (options :yes))
                          (prompt-user-yn (get-msg :service-delete-confirm
                                                   service-name)))
              (fail (get-msg :delete-cancel)))))
        ;; Delete the key if it exists
        (println (get-msg :deleting-rnr-key service-name))
        (cf/delete-service-key cf-auth key-guid))

      (println (get-msg :deleting-rnr-service service-name))
      (cf/delete-service cf-auth guid)
      ;; Delete service information from the state
      (delete-user-selection (update-in state [:services] dissoc kw)
                             (keyword type) (fn [_] true))
      (str new-line (get-msg :rnr-deleted service-name) new-line))))

(defmethod delete :document-conversion
  [state [cmd what service-name & args] flags]
  (reject-extra-args args cmd what)
  (let [options (get-options flags delete-options)]
    (when (empty? service-name)
      (fail (get-msg :missing-dc-name)))
    (let [kw (keyword service-name)
          {:keys [guid key-guid type]} (-> state :services kw)
          cf-auth (cf/generate-auth state)]
      (when (nil? guid)
        (fail (get-msg :unknown-dc service-name)))
      (when-not (= type "document_conversion")
        (fail (get-msg :not-dc-service service-name)))

      (when (some? key-guid)
        ;; Delete the key if it exists
        (println (get-msg :deleting-dc-key service-name))
        (cf/delete-service-key cf-auth key-guid))

      (println (get-msg :deleting-dc-service service-name))
      (cf/delete-service cf-auth guid)
      ;; Delete service information from the state
      (delete-user-selection (update-in state [:services] dissoc kw)
                             (keyword type) (fn [_] true))
      (str new-line (get-msg :dc-deleted service-name) new-line))))

(defmethod delete :cluster
  ;; Delete a Solr cluster given its name.

  ;; The unfortunate thing here is that the retrieve_and_rank service
  ;; allows multiple clusters with the same name. This command will delete
  ;; the first cluster with a matching name that it happens to find.

  ;; We could consider enhancing this to fail when multiple matches are found.
  ;; Then we would have to give the user some other way to delete a cluster.
  ;; This command could accept a solr_cluster_id."

  [state [cmd what cluster-name & args] flags]
  (reject-extra-args args cmd what)
  (let [options (get-options flags delete-options)]
    (when-not cluster-name
      (fail (get-msg :missing-cluster-name)))
    (let [[service-key service-details] (my/rnr-service state)
          credentials (:credentials service-details)]
      (when-not service-key
        (fail (get-msg :unknown-cluster-rnr)))
      (let [clusters (rnr/list-clusters credentials)
            cluster-info (first (filter #(= cluster-name (:cluster_name %))
                                        clusters))]
        (when-not cluster-info
          (fail (get-msg :unknown-cluster cluster-name (name service-key))))

        (let [cluster-id (:solr_cluster_id cluster-info)
              num-configs (count (rnr/list-configs credentials cluster-id))
              num-collections (count (rnr/list-collections
                                      credentials cluster-id))]
          (when-not (and (zero? num-configs) (zero? num-collections))
            (println (get-msg :cluster-obj-num num-configs num-collections))
            (when-not (or (some? (options :yes))
                          (prompt-user-yn (get-msg :cluster-delete-confirm
                                                   cluster-name)))
              (fail (get-msg :delete-cancel)))))
        (rnr/delete-cluster credentials (:solr_cluster_id cluster-info))
        (delete-user-selection state :cluster
                               #(= cluster-name (:cluster_name %)))
        (str new-line
             (get-msg :cluster-deleted cluster-name (name service-key))
             new-line)))))

(defmethod delete :solr-configuration
  [state [cmd what config-name & args] flags]
  (reject-extra-args args cmd what)
  (get-options flags {})
  (when-not config-name
    (fail (get-msg :missing-config-name)))
  (let [{:keys [service-key cluster_name solr_cluster_id]} (my/cluster state)]
    (when-not service-key
      (fail (get-msg :unknown-config-cluster)))
    (rnr/delete-config
     (my/creds-for-service state (keyword service-key))
     solr_cluster_id config-name)
    (delete-user-selection state :config
                           #(= config-name (:config-name %)))
    (str new-line
         (get-msg :config-deleted config-name (name service-key) cluster_name)
         new-line)))

(defmethod delete :collection
  [state [cmd what collection-name & args] flags]
  (reject-extra-args args cmd what)
  (get-options flags {})
  (when-not collection-name
    (fail (get-msg :missing-collection-name)))
  (let [{:keys [service-key cluster_name solr_cluster_id]} (my/cluster state)]
    (when-not service-key
      (fail (get-msg :unknown-collection-cluster)))
    (rnr/delete-collection (my/creds-for-service state (keyword service-key))
                           solr_cluster_id collection-name)
    (delete-user-selection state :collection
                           #(= collection-name (:collection-name %)))
    (str new-line
         (get-msg :collection-deleted
                  collection-name (name service-key) cluster_name)
         new-line)))
