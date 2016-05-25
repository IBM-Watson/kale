;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.create
  (:require [cheshire.core :as json]
            [kale.aliases :as aliases]
            [kale.cloud-foundry :as cf]
            [kale.common :refer [fail readable-files? new-line
                                get-options reject-extra-args
                                unknown-action get-command-msg]]
            [kale.getter :as my]
            [kale.persistence :as persist]
            [kale.retrieve-and-rank :as rnr]
            [kale.update :refer [update-user-selection]]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn get-msg
  "Return the corresponding create message"
   [msg-key & args]
   (apply get-command-msg :create-messages msg-key args))

(def create-items
  {:cluster aliases/cluster
   :collection aliases/collection
   :crawler-configuration aliases/crawler-configuration
   :document-conversion aliases/document-conversion
   :retrieve-and-rank aliases/retrieve-and-rank
   :solr-configuration aliases/solr-configuration
   :space aliases/space})

(defmulti create (fn [_ [_ what & _] _]
                   (or (some (fn [[k a]] (when (a what) k)) create-items)
                       :unknown)))

(defmethod create :unknown
  [state [cmd what & args] flags]
  (unknown-action what cmd
                 ["space" "document_conversion" "retrieve_and_rank"
                  "cluster" "solr-configuration" "collection"
                  "crawler-configuration"]))

(defmethod create :space
  [state [cmd what space-name & args] flags]
  (reject-extra-args args cmd what)
  (get-options flags {})
  (when (empty? space-name)
    (fail (get-msg :missing-space-name)))
  (let [cf-auth (cf/generate-auth state)
        org-guid (-> state :org-space :guid :org)
        user-guid (cf/get-user-guid (cf-auth :token))
        space (cf/create-space cf-auth org-guid user-guid space-name)
        space-guid (-> space :metadata :guid)]
    (persist/write-state (merge state
                                {:services {}
                                 :org-space
                                  {:org (-> state :org-space :org)
                                   :space space-name
                                   :guid
                                    {:org org-guid
                                     :space space-guid}}}))
    (get-msg :space-created space-name)))

(defn create-service-with-plan
  "Create an instance of a service using the specified plan"
  [cf-auth space-guid service-type service-name plan-name]
  (if-let [plan-guid (cf/get-service-plan-guid
                   cf-auth space-guid service-type plan-name)]
    (do (println (get-msg :creating-service
                          service-type service-name plan-name))
        (cf/create-service cf-auth service-name space-guid plan-guid))
    (fail (get-msg :plan-not-available plan-name service-type))))

(defn wait-for-service
  "Wait for the service to complete being provisioned"
  [cf-auth service-guid]
  (let [status (atom "create in progress")]
    (while (= @status "create in progress")
      (reset! status (cf/get-service-status cf-auth service-guid))
      (print ".")
      (flush))
    (println)
    (when (= @status "create failed")
      (fail (get-msg :create-failed)))))

(defn create-key-for-service
  "Create a service-key for the given service guid"
   [cf-auth service-name service-guid]
   (println (get-msg :creating-key service-name))
   (cf/create-service-key cf-auth service-name service-guid))

(defn create-service-with-key
  "Create an instance of a service with credentials"
  [state service-type service-name enterprise?]
  (when (empty? service-name)
    (fail (get-msg :missing-service-name)))
  (let [cf-auth (cf/generate-auth state)
        space-guid (-> state :org-space :guid :space)
        ; Create the service
        service-plan (if enterprise? "enterprise" "standard")
        service (create-service-with-plan
                   cf-auth space-guid service-type service-name service-plan)
        service-guid (-> service :metadata :guid)
        ; Wait for service creation to complete
        _ (when enterprise? (wait-for-service cf-auth service-guid))

        ; Create the service-key
        service-key (create-key-for-service cf-auth service-name service-guid)
        key-guid (-> service-key :metadata :guid)
        credentials (-> service-key :entity :credentials)
        ; Create the new service entry and write it
        service-entry {(keyword service-name) {:guid service-guid
                                               :type service-type
                                               :plan service-plan
                                               :key-guid key-guid
                                               :credentials credentials}}]
    (update-user-selection (update-in state [:services] merge service-entry)
                           (keyword service-type)
                           service-name))
    (get-msg :service-created service-name))

(def create-service-options {
  :enterprise aliases/enterprise-option})

(defmethod create :document-conversion
  [state [cmd what service-name & args] flags]
  (reject-extra-args args cmd what)
  (let [options (get-options flags create-service-options)]
    (create-service-with-key state
                             "document_conversion"
                             service-name
                             (some? (options :enterprise)))))

(defmethod create :retrieve-and-rank
  [state [cmd what service-name & args] flags]
  (reject-extra-args args cmd what)
  (let [options (get-options flags create-service-options)]
    (create-service-with-key state
                             "retrieve_and_rank"
                             service-name
                             (some? (options :enterprise)))))

(defmethod create :cluster
  [state [cmd what cluster-name cluster-size & args] flags]
  (reject-extra-args args cmd what)
  (get-options flags {})
  (when-not cluster-name
    (fail (get-msg :missing-cluster-name)))
  (rnr/validate-solr-name cluster-name)
  (when cluster-size
    (try (let [i (Integer. cluster-size)]
           (when-not (< 0 i 8)
             (fail (get-msg :cluster-size))))
         (catch NumberFormatException e
           (fail (get-msg :cluster-size)))))
  (let [[service-key service-details] (my/rnr-service state)]
    (when-not service-key
      (fail (get-msg :unknown-rnr-service)))
    (when (some #(= cluster-name (:cluster_name %))
                (rnr/list-clusters (:credentials service-details)))
      (fail (get-msg :existing-cluster cluster-name)))
    (println (get-msg :creating-cluster cluster-name (name service-key)))
    (let [cluster (rnr/create-cluster (:credentials service-details)
                                      cluster-name cluster-size)]
      (update-user-selection state
                             :cluster
                             (merge {:service-key (name service-key)}
                                    cluster)))
    (get-msg :cluster-created cluster-name)))

(defmethod create :solr-configuration
  [state [cmd what config-name config-zip & args] flags]
  (reject-extra-args args cmd what)
  (get-options flags {})
  (when-not config-name
    (fail (get-msg :missing-config-name)))
  (rnr/validate-solr-name config-name)
  (let [the-zip (if config-zip
                  (do (readable-files? [config-zip])
                      (io/file config-zip))
                  (let [resource (io/resource (str config-name ".zip"))]
                    (when-not resource
                      (fail (get-msg :unknown-packaged-config config-name)))
                    (io/input-stream resource)))
        {:keys [service-key solr_cluster_id cluster_name]} (my/cluster state)]
    (when-not service-key
      (fail (get-msg :unknown-cluster-config)))
    (println (get-msg :creating-config
                      config-name (name service-key) cluster_name))
    (rnr/upload-config (my/creds-for-service state (keyword service-key))
                       solr_cluster_id
                       config-name
                       the-zip)
    (update-user-selection state
                           :config
                           {:service-key (name service-key)
                            :cluster-id solr_cluster_id
                            :config-name config-name}))
  (get-msg :config-created config-name))

(defmethod create :collection
  [state [cmd what collection-name & args] flags]
  (reject-extra-args args cmd what)
  (get-options flags {})
  (when-not collection-name
    (fail (get-msg :missing-collection-name)))
  (let [{:keys [service-key cluster_name solr_cluster_id]} (my/cluster state)
        {:keys [config-name]} (my/solr-configuration state)]
    (when-not service-key
      (fail (get-msg :unknown-cluster-collection)))
    (when-not config-name
      (fail (get-msg :unknown-config)))
    (println (get-msg :creating-collection
                      collection-name (name service-key)
                      cluster_name config-name))
    (rnr/create-collection
      (my/creds-for-service state (keyword service-key))
      solr_cluster_id config-name collection-name)
    (update-user-selection state
                           :collection
                           {:service-key service-key
                            :cluster-id solr_cluster_id
                            :cluster-name cluster_name
                            :collection-name collection-name})
    (get-msg :collection-created collection-name)))

(defn fail-missing-item
  [item]
  (fail (get-msg :missing-item item item)))

(defn rnr-portion
  "Generate the Retrieve and Rank portion of the Orchestration
   configuration that comes from our information."
  [{:keys [services]}
   {:keys [cluster-id collection-name service-key]}]

  (let [rnr-key (keyword service-key)
        {:keys [guid]} (rnr-key services)]
    {:cluster_id cluster-id
     :search_collection collection-name
     :service_instance_id guid
     :fields {:include [:body
                        :contentHtml
                        :contentText
                        :id
                        :indexedTimestamp
                        :searchText
                        :sourceUrl
                        :title]}}))

(defn get-orchestration-config
  "Get the configuration for orchestration, returns a string containing JSON.
   For failures, display a message to the user and throw an exception."
  [state collection]
  (update-in (my/conversion-configuration state)
             [:retrieve_and_rank]
             #(merge % (rnr-portion state collection))))

(defn orchestration-service
  [state conversion-service]
  (let [version (my/index-document-version state)
        {:keys [url username password]}
        (get-in conversion-service [1 :credentials])]
    {:http_timeout 600
     :concurrent_upload_connection_limit 100
     :base_url url
     :endpoint (str "/v1/index_document?version=" version)
     :credentials {:username username
                   :password password}
     :config_file "orchestration_service_config.json"}))

(defn write-json
  [file json]
  (spit file (json/encode json {:pretty true})))

(defmethod create :crawler-configuration
  [state [cmd what & args] flags]
  (reject-extra-args args cmd what)
  (get-options flags {})
  (let [collection (my/collection state)
        conversion-service (my/conversion-service state)]
    (when-not collection
      (fail-missing-item (get-msg :collection-item)))
    (when-not conversion-service
      (fail-missing-item (get-msg :dc-service-item)))
    (write-json "orchestration_service_config.json"
                (get-orchestration-config state collection))
    (write-json "orchestration_service.conf"
                (orchestration-service state conversion-service)))
  (get-msg :crawl-config-created))
