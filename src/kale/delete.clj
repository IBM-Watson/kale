;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.delete
  (:require [kale.common :refer [fail new-line
                                 prompt-user-yn get-options
                                 reject-extra-args unknown-action]]
            [kale.aliases :as aliases]
            [kale.getter :as my]
            [kale.cloud-foundry :as cf]
            [kale.update :refer [delete-user-selection]]
            [kale.retrieve-and-rank :as rnr]
            [kale.persistence :as persist]))

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
      (fail "Please specify the name of the space to delete."))
    (when (= space-name (-> state :org-space :space))
      (fail "You cannot delete the space you are currently working in."))
    (let [cf-auth (cf/generate-auth state)
          org-guid (-> state :org-space :guid :org)
          spaces (cf/get-spaces cf-auth org-guid)
          space (cf/find-entity spaces space-name)
          space-guid (-> space :metadata :guid)]
      (when (nil? space)
        (fail (str "No space named '" space-name "' was found.")))

      ;; Check to see if the space contains services
      (let [services (cf/get-services cf-auth space-guid)
            num-services (count (keys services))]
        (when-not (zero? num-services)
          (println (str "This space contains " num-services " service(s)."))
          (when-not (or (some? (options :yes))
                        (prompt-user-yn (str "Are you sure you want "
                                             "to delete space '"
                                             space-name "'")))
            (fail "Deletion cancelled."))))
      (cf/delete-space cf-auth space-guid)
      (str "Deletion initiated for space '" space-name "'."
           new-line "The space will be deleted shortly."))))

(defmethod delete :retrieve-and-rank
  [state [cmd what service-name & args] flags]
  (reject-extra-args args cmd what)
  (let [options (get-options flags delete-options)]
    (when (empty? service-name)
      (fail
       "Please specify the name of the retrieve_and_rank service to delete."))
    (let [kw (keyword service-name)
          {:keys [guid key-guid type credentials]} (-> state :services kw)
          cf-auth (cf/generate-auth state)]
      (when (nil? guid)
        (fail (str "No retrieve_and_rank service named '"
                   service-name "' was found.")))
      (when-not (= type "retrieve_and_rank")
        (fail (str "The service named '" service-name
                   "' is not a retrieve_and_rank service.")))

      ;; We need credentials to check if the service has clusters
      (when (some? key-guid)
        (let [num-clusters (count (rnr/list-clusters credentials))]
          (when-not (zero? num-clusters)
            (println (str "This retrieve_and_rank instance contains "
                          num-clusters " cluster(s)."))
            (when-not (or (some? (options :yes))
                          (prompt-user-yn (str "Are you sure you want "
                                               "to delete service '"
                                               service-name "'")))
              (fail "Deletion cancelled."))))
        ;; Delete the key if it exists
        (println (str "Deleting key for retrieve_and_rank service '"
                      service-name "'."))
        (cf/delete-service-key cf-auth key-guid))

      (println (str "Deleting retrieve_and_rank service '" service-name "'."))
      (cf/delete-service cf-auth guid)
      ;; Delete service information from the state
      (delete-user-selection (update-in state [:services] dissoc kw)
                             (keyword type) (fn [_] true))
      (str "Deletion initiated for retrieve_and_rank service '" service-name
           "'." new-line "The service will be deleted shortly."))))

(defmethod delete :document-conversion
  [state [cmd what service-name & args] flags]
  (reject-extra-args args cmd what)
  (let [options (get-options flags delete-options)]
    (when (empty? service-name)
      (fail
       "Please specify the name of the document_conversion service to delete."))
    (let [kw (keyword service-name)
          {:keys [guid key-guid type]} (-> state :services kw)
          cf-auth (cf/generate-auth state)]
      (when (nil? guid)
        (fail (str "No document_conversion service named '"
                   service-name "' was found.")))
      (when-not (= type "document_conversion")
        (fail (str "The service named '" service-name
                   "' is not a document_conversion service.")))

      (when (some? key-guid)
        ;; Delete the key if it exists
        (println (str "Deleting key for document_conversion service '"
                      service-name "'."))
        (cf/delete-service-key cf-auth key-guid))

      (println (str "Deleting document_conversion service '" service-name "'."))
      (cf/delete-service cf-auth guid)
      ;; Delete service information from the state
      (delete-user-selection (update-in state [:services] dissoc kw)
                             (keyword type) (fn [_] true))
      (str "Deletion initiated for document_conversion service '" service-name
           "'." new-line "The service will be deleted shortly."))))

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
      (fail "Please specify the name of the cluster to delete."))
    (let [[service-key service-details] (my/rnr-service state)
          credentials (:credentials service-details)]
      (when-not service-key
        (fail (str "Couldn't determine which service to delete the "
                   "cluster from." new-line
                   "Please select a retrieve_and_rank service.")))
      (let [clusters (rnr/list-clusters credentials)
            cluster-info (first (filter #(= cluster-name (:cluster_name %))
                                        clusters))]
        (when-not cluster-info
          (fail (str "Didn't find cluster '" cluster-name
                     "' in '" (name service-key) "'.")))

        (let [cluster-id (:solr_cluster_id cluster-info)
              num-configs (count (rnr/list-configs credentials cluster-id))
              num-collections (count (rnr/list-collections
                                      credentials cluster-id))]
          (when-not (and (zero? num-configs) (zero? num-collections))
            (println (str "This cluster contains "
                          num-configs " Solr configuration(s) and "
                          num-collections" collection(s)."))
            (when-not (or (some? (options :yes))
                          (prompt-user-yn (str "Are you sure you want "
                                               "to delete cluster '"
                                                cluster-name "'")))
              (fail "Deletion cancelled."))))
        (rnr/delete-cluster credentials (:solr_cluster_id cluster-info))
        (delete-user-selection state :cluster
                               #(= cluster-name (:cluster_name %)))
        (str "Cluster '" cluster-name "' has been deleted from '"
             (name service-key) "'.")))))

(defmethod delete :solr-configuration
  [state [cmd what config-name & args] flags]
  (reject-extra-args args cmd what)
  (get-options flags {})
  (when-not config-name
    (fail "Please specify the name of the Solr configuration to delete."))
  (let [{:keys [service-key cluster_name solr_cluster_id]} (my/cluster state)]
    (when-not service-key
      (fail (str "Couldn't determine which cluster to delete "
                 "the configuration from." new-line
                 "Please select a Solr cluster to work with.")))
    (rnr/delete-config
     (my/creds-for-service state (keyword service-key))
     solr_cluster_id config-name)
    (delete-user-selection state :config
                           #(= config-name (:config-name %)))
    (str "Solr configuration '" config-name "' has been deleted from '"
         (name service-key) "/" cluster_name "'.")))

(defmethod delete :collection
  [state [cmd what collection-name & args] flags]
  (reject-extra-args args cmd what)
  (get-options flags {})
  (when-not collection-name
    (fail "Please specify the name of the collection to delete."))
  (let [{:keys [service-key cluster_name solr_cluster_id]} (my/cluster state)]
    (when-not service-key
      (fail (str "Couldn't determine which cluster to delete the collection "
                 "from." new-line "Please select a Solr cluster.")))
    (rnr/delete-collection (my/creds-for-service state (keyword service-key))
                           solr_cluster_id collection-name)
    (delete-user-selection state :collection
                           #(= collection-name (:collection-name %)))
    (str "Collection '" collection-name "' has been deleted from '"
         (name service-key) "/" cluster_name "'.")))
