;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.create
  (:require [cheshire.core :as json]
            [clj-time.core :as t]
            [kale.aliases :as aliases]
            [kale.cloud-foundry :as cf]
            [kale.select :refer [select]]
            [kale.delete :refer [delete]]
            [kale.common :refer [fail readable-files? new-line
                                get-options reject-extra-args
                                unknown-action get-command-msg
                                try-function]]
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
   :space aliases/space
   :wizard aliases/wizard})

(defmulti create (fn [_ [_ what & _] _]
                   (or (some (fn [[k a]] (when (a what) k)) create-items)
                       :unknown)))

(defmethod create :unknown
  [state [cmd what & args] flags]
  (unknown-action what cmd
                 ["space" "document_conversion" "retrieve_and_rank"
                  "cluster" "solr-configuration" "collection"
                  "crawler-configuration" "wizard"]))

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
    (str new-line (get-msg :space-created space-name) new-line)))

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
    (str new-line (get-msg :service-created service-name) new-line))

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

(defn wait-for-cluster
  "Wait for the cluster to become ready"
  [endpoint cluster-id]
  (let [start-time (t/now)
        update-time (atom (t/now))
        wait-time (fn [target] (t/in-minutes (t/interval target (t/now))))]
    (while (and (= "NOT_AVAILABLE"
                   (:solr_cluster_status (rnr/get-cluster endpoint
                                                          cluster-id)))
                (< (wait-time start-time) 30))
      (print ".")
      (when (>= (wait-time @update-time) 5)
        ;; Inform the user that we're still waiting
        (println (str new-line (get-msg :still-waiting-on-cluster)))
        (reset! update-time (t/now)))
      (flush))
    (when (>= (wait-time start-time) 30)
      (fail (get-msg :cluster-timed-out)))
    ;; Sadly there is a small delay between when a cluster saying that it
    ;; is ready and when it is *actually* ready.
    (Thread/sleep 4000)
    (println)))

(def create-cluster-options {
  :wait aliases/wait-option})

(defmethod create :cluster
  [state [cmd what cluster-name cluster-size & args] flags]
  (reject-extra-args args cmd what)
  (let [options (get-options flags create-cluster-options)]
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
      (let [credentials (:credentials service-details)
            cluster (rnr/create-cluster credentials cluster-name cluster-size)]
        (update-user-selection state
                               :cluster
                               (merge {:service-key (name service-key)}
                                      cluster))
        (if (some? (options :wait))
          (do (println (get-msg :waiting-on-cluster))
              (wait-for-cluster credentials (:solr_cluster_id cluster))
              (str new-line (get-msg :cluster-created cluster-name)
                   new-line))
          (str new-line (get-msg :cluster-created-soon cluster-name)
               new-line))))))

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
  (str new-line (get-msg :config-created config-name) new-line))

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
    (str new-line (get-msg :collection-created collection-name) new-line)))

(defn wizard-command
  "Run the specified command and rollback if it fails"
  [state command args flags rollback]
  (println (get-msg :wizard-running-cmd
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

(defmethod create :wizard
  [state [cmd what base-name config-name config-zip & args] flags]
  (reject-extra-args args cmd what)
  (let [options (get-options flags create-service-options)
        starting-space (-> state :org-space :space)]
    (when-not base-name
      (fail (get-msg :missing-wizard-name)))
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

    ;; Create the space to put the instance in
    (wizard-command
      state create ["create" "space" base-name] []
      (fn [] (fail (get-msg :wizard-failure base-name))))
    ;; Create the individual components in the newly made space
    (run-wizard
      [[create ["create" "document_conversion" (str base-name "-dc")] []]
       [create ["create" "retrieve_and_rank" (str base-name "-rnr")] []]
       [create ["create" "cluster" (str base-name "-cluster")] ["--wait"]]
       [create (concat ["create" "solr-configuration" config-name]
                       (if (some? config-zip) [config-zip] [])) []]
       [create ["create" "collection" (str base-name "-collection")] []]]
      ;; Rollback
      (fn [] (println (str new-line (get-msg :wizard-rollback)))
             (run-wizard [[select ["select" "space" starting-space] []]
                          [delete ["delete" "space" base-name] ["--y"]]]
                         (fn [] nil))
             (fail (get-msg :wizard-failure base-name))))
    (get-msg :wizard-success base-name)))

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
    {:base_url url
     :concurrent_upload_connection_limit 100
     :config_file "orchestration_service_config.json"
     :credentials {:username username :password password}
     :endpoint (str "/v1/index_document?version=" version)
     :http_timeout 600
     :send_stats {:jvm true :os true}}))

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
  (str new-line (get-msg :crawl-config-created) new-line))
