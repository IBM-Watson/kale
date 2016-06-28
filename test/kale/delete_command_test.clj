;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.delete-command-test
  (:require [kale.delete-command :as sut]
            [kale.common :refer [new-line] :as common]
            [kale.cloud-foundry :as cf]
            [kale.cloud-foundry-constants :as c]
            [kale.persistence :as persist]
            [clojure.test :refer [deftest is]]
            [slingshot.slingshot :refer [throw+]]
            [slingshot.test :refer :all]
            [kale.retrieve-and-rank :as rnr]
            [kale.persistence :as persist]))

(common/set-language :en)

(def default-state {:login {:cf-token "TOKEN"
                            :endpoint "URL"}
                    :services (merge c/entry1 c/entry2)
                    :org-space {:org "org-name"
                                :space "space-name"
                                :guid {:org "ORG_GUID",
                                       :space "SPACE_GUID"}}})

(deftest unknown-delete-target
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"Don't know how to 'delete junk'"
       (sut/delete-command {} ["delete" "junk"] []))))

(deftest delete-space-missing-name
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"Please specify the name of the space to delete."
       (sut/delete-command default-state ["delete" "space"] []))))

(deftest delete-space-current-space
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"You cannot delete the space you are currently working in."
       (sut/delete-command default-state ["delete" "space" "space-name"] []))))

(deftest delete-space-bad-name
  (with-redefs [cf/get-spaces (fn [_ _] (c/spaces-response :resources))]
    (is (thrown+-with-msg?
         [:type :kale.common/fail]
         #"No space named 'space0' was found."
         (sut/delete-command default-state ["delete" "space" "space0"] [])))))

(deftest delete-space-no-services
  (with-redefs [cf/get-spaces (fn [_ _] (c/spaces-response :resources))
                cf/get-services (fn [_ _] {})
                cf/delete-space (fn [_ space-guid]
                                  (is (= space-guid "SPACE_GUID1")))]
    (is (= (str new-line "Deletion initiated for space 'space1'."
                new-line "The space will be deleted shortly."
                new-line)
           (sut/delete-command default-state ["delete" "space" "space1"] [])))))

(deftest delete-space-with-services-no
  (with-redefs [cf/get-spaces (fn [_ _] (c/spaces-response :resources))
                cf/get-services (fn [_ _] {:some-service {}})
                common/prompt-user-yn (fn [_] false)]
    (is (= (str "This space contains 1 service(s)." new-line)
           (with-out-str
             (is (thrown+-with-msg?
                  [:type :kale.common/fail]
                  #"Deletion cancelled."
                  (sut/delete-command default-state
                                      ["delete" "space" "space1"]
                                      []))))))))

(deftest delete-space-with-services-flag-set
  (with-redefs [cf/get-spaces (fn [_ _] (c/spaces-response :resources))
                cf/get-services (fn [_ _] {:some-service {}})
                common/prompt-user-yn (fn [_] (common/fail "No prompt!"))
                cf/delete-space (fn [_ space-guid]
                                  (is (= space-guid "SPACE_GUID1")))]
    (is (= (str "This space contains 1 service(s)." new-line)
           (with-out-str
             (is (= (str new-line "Deletion initiated for space 'space1'."
                         new-line "The space will be deleted shortly."
                         new-line)
                    (sut/delete-command default-state
                                        ["delete" "space" "space1"]
                                        ["--yes"]))))))))

(deftest delete-space-with-services-yes
  (with-redefs [cf/get-spaces (fn [_ _] (c/spaces-response :resources))
                cf/get-services (fn [_ _] {:some-service {}})
                common/prompt-user-yn (fn [_] true)
                cf/delete-space (fn [_ space-guid]
                                  (is (= space-guid "SPACE_GUID1")))]
    (is (= (str "This space contains 1 service(s)." new-line)
           (with-out-str
             (is (= (str new-line "Deletion initiated for space 'space1'."
                         new-line "The space will be deleted shortly."
                         new-line)
                    (sut/delete-command default-state
                                        ["delete" "space" "space1"]
                                        []))))))))

(deftest delete-rnr-service-missing-name
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"Please specify the name of the retrieve_and_rank service to delete."
       (sut/delete-command default-state ["delete" "rnr"] []))))

(deftest delete-dc-service-missing-name
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"Please specify the name of the document_conversion service to delete."
       (sut/delete-command default-state ["delete" "dc"] []))))

(deftest delete-rnr-bad-name
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"No retrieve_and_rank service named 'service-name0' was found."
       (sut/delete-command default-state ["delete" "rnr" "service-name0"] []))))

(deftest delete-dc-bad-name
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"No document_conversion service named 'service-name0' was found."
       (sut/delete-command default-state ["delete" "dc" "service-name0"] []))))

(deftest delete-rnr-wrong-kind
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"The service named 'dc-service' is not a retrieve_and_rank service."
       (sut/delete-command default-state ["delete" "rnr" "dc-service"] []))))

(deftest delete-dc-wrong-kind
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"The service named 'rnr-service' is not a document_conversion service."
       (sut/delete-command default-state ["delete" "dc" "rnr-service"] []))))

(deftest delete-rnr-with-clusters-no
  (with-redefs [rnr/list-clusters (fn [_] [{:cluster_name "cluster-name"}])
                common/prompt-user-yn (fn [_] false)]
    (is (= (str "This retrieve_and_rank instance contains 1 cluster(s)."
                new-line)
           (with-out-str
             (is (thrown+-with-msg?
                  [:type :kale.common/fail]
                  #"Deletion cancelled."
                  (sut/delete-command (update-in default-state
                                                 [:services :rnr-service]
                                                 assoc
                                                 :type
                                                 "retrieve_and_rank")
                                      ["delete" "rnr" "rnr-service"]
                                      []))))))))

(deftest delete-rnr-with-clusters-flag-set
  (with-redefs [rnr/list-clusters (fn [_] [{:cluster_name "cluster-name"}])
                common/prompt-user-yn (fn [_] (common/fail "No prompt!"))
                cf/delete-service-key (fn [_ _])
                cf/delete-service (fn [_ _])
                persist/write-state (fn [_])]
    (is (= (str "This retrieve_and_rank instance contains 1 cluster(s)."
                new-line
                "Deleting key for retrieve_and_rank service 'rnr-service'."
                new-line "Deleting retrieve_and_rank service 'rnr-service'."
                new-line)
           (with-out-str
             (sut/delete-command (update-in default-state
                                            [:services :rnr-service]
                                            assoc :type "retrieve_and_rank")
                                 ["delete" "rnr" "rnr-service"]
                                 ["--yes"]))))))

(deftest delete-rnr-with-clusters-yes
  (with-redefs [rnr/list-clusters (fn [_] [{:cluster_name "cluster-name"}])
                common/prompt-user-yn (fn [_] true)
                cf/delete-service-key (fn [_ _])
                cf/delete-service (fn [_ _])
                persist/write-state (fn [_])]
    (is (= (str "This retrieve_and_rank instance contains 1 cluster(s)."
                new-line
                "Deleting key for retrieve_and_rank service 'rnr-service'."
                new-line "Deleting retrieve_and_rank service 'rnr-service'."
                new-line)
           (with-out-str
             (sut/delete-command (update-in default-state
                                            [:services :rnr-service]
                                            assoc :type "retrieve_and_rank")
                                 ["delete" "rnr" "rnr-service"]
                                 []))))))

(deftest delete-rnr-service
  (let [output-state (atom {})]
    (with-redefs [rnr/list-clusters (fn [_] [])
                  cf/delete-service-key (fn [_ key-guid]
                                          (is (= key-guid "KEY_RNR_GUID")))
                  cf/delete-service (fn [_ guid]
                                      (is (= guid "RNR_GUID")))
                  persist/write-state (fn [state] (reset! output-state state))]
      (is (= (str "Deleting key for retrieve_and_rank service 'rnr-service'."
                  new-line
                  "Deleting retrieve_and_rank service 'rnr-service'." new-line)
             (with-out-str
               (is (= (str new-line "Deletion initiated for retrieve_and_rank"
                           " service 'rnr-service'."
                           new-line "The service will be deleted shortly."
                           new-line)
                      (sut/delete-command default-state
                                          ["delete" "rnr" "rnr-service"]
                                          []))))))
      (is (= (merge (update-in default-state [:services] dissoc :rnr-service)
                    {:user-selections nil})
             @output-state)))))

(deftest delete-dc-service
  (let [output-state (atom {})]
    (with-redefs [rnr/list-clusters (fn [_] [])
                  cf/delete-service-key (fn [_ key-guid]
                                          (is (= key-guid "KEY_DC_GUID")))
                  cf/delete-service (fn [_ guid]
                                      (is (= guid "DC_GUID")))
                  persist/write-state (fn [state] (reset! output-state state))]
      (is (= (str "Deleting key for document_conversion service 'dc-service'."
                  new-line
                  "Deleting document_conversion service 'dc-service'."
                  new-line)
             (with-out-str
               (is (= (str new-line "Deletion initiated for document_conversion"
                           " service 'dc-service'."
                           new-line "The service will be deleted shortly."
                           new-line)
                      (sut/delete-command default-state
                                          ["delete" "dc" "dc-service"]
                                          []))))))
      (is (= (merge (update-in default-state [:services] dissoc :dc-service)
                    {:user-selections nil})
             @output-state)))))

(deftest delete-rnr-service-remove-from-selections
  (let [state-with-selections (merge default-state
                                     {:user-selections
                                      {:retrieve_and_rank "rnr-service"}})
        output-state (atom {})]
    (with-redefs [rnr/list-clusters (fn [_] [])
                  cf/delete-service-key (fn [_ _])
                  cf/delete-service (fn [_ _])
                  persist/write-state (fn [state] (reset! output-state state))]
      (with-out-str (sut/delete-command state-with-selections
                                        ["delete" "rnr" "rnr-service"]
                                        []))
      (is (= (merge (update-in default-state [:services] dissoc :rnr-service)
                    {:user-selections {}})
             @output-state)))))

(deftest delete-dc-service-remove-from-selections
  (let [state-with-selections (merge default-state
                                     {:user-selections
                                      {:document_conversion "dc-service"}})
        output-state (atom {})]
    (with-redefs [cf/delete-service-key (fn [_ _])
                  cf/delete-service (fn [_ _])
                  persist/write-state (fn [state] (reset! output-state state))]
      (with-out-str (sut/delete-command state-with-selections
                                        ["delete" "dc" "dc-service"]
                                        []))
      (is (= (merge (update-in default-state [:services] dissoc :dc-service)
                    {:user-selections {}})
             @output-state)))))

(deftest delete-cluster-missing-name
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"Please specify the name of the cluster to delete."
       (sut/delete-command {} ["delete" "cluster"] []))))

(deftest delete-cluster-no-service
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"Couldn't determine which service to delete the cluster from."
       (sut/delete-command {} ["delete" "cluster" "cluster-name"] []))))

(deftest delete-cluster-not-found
  (with-redefs [rnr/list-clusters (fn [_] [{:solr_cluster_id "id"
                                            :cluster_name "other-cluster"}])]
    (is (thrown+-with-msg?
         [:type :kale.common/fail]
         #"Didn't find cluster 'cluster-name' in 'rnr'."
         (sut/delete-command {:services {:rnr {:type "retrieve_and_rank"}}
                              :user-selections {:retrieve_and_rank "rnr"}}
                             ["delete" "cluster" "cluster-name"]
                             [])))))

(deftest delete-cluster-with-elements-no
  (with-redefs [rnr/list-clusters (fn [_] [{:solr_cluster_id "id"
                                            :cluster_name "cluster-name"}])
                rnr/list-configs (fn [_ _] [{:config-name "config-name"}])
                rnr/list-collections (fn [_ _] [{:collection-name
                                                 "collection-name"}])
                common/prompt-user-yn (fn [_] false)]
    (is (= (str "This cluster contains 1 Solr configuration(s) and "
                "1 collection(s)." new-line)
           (with-out-str
             (is (thrown+-with-msg?
                  [:type :kale.common/fail]
                  #"Deletion cancelled."
                  (sut/delete-command
                   {:services {:rnr {:type "retrieve_and_rank"}}
                    :user-selections
                    {:retrieve_and_rank "rnr"
                     :cluster {:service-key :rnr
                               :solr_cluster_id "id"
                               :cluster_name "cluster-name"}}}
                   ["delete" "cluster" "cluster-name"]
                   []))))))))

(deftest delete-cluster-with-elements-flag-set
  (with-redefs [rnr/list-clusters (fn [_] [{:solr_cluster_id "id"
                                            :cluster_name "cluster-name"}])
                rnr/list-configs (fn [_ _] [{:config-name "config-name"}])
                rnr/list-collections (fn [_ _] [{:collection-name
                                                 "collection-name"}])
                common/prompt-user-yn (fn [_] (common/fail "No prompt!"))
                rnr/delete-cluster (fn [_ _])
                persist/write-state (fn [_])]
    (is (= (str "This cluster contains 1 Solr configuration(s) and "
                "1 collection(s)." new-line)
           (with-out-str
             (is (= (str new-line "Cluster 'cluster-name' has been deleted"
                         " from 'rnr'." new-line)
                    (sut/delete-command
                     {:services {:rnr {:type "retrieve_and_rank"}}
                      :user-selections
                      {:retrieve_and_rank "rnr"
                       :cluster {:service-key :rnr
                                 :solr_cluster_id "id"
                                 :cluster_name "cluster-name"}}}
                     ["delete" "cluster" "cluster-name"]
                     ["--yes"]))))))))

(deftest delete-cluster-with-elements-yes
  (with-redefs [rnr/list-clusters (fn [_] [{:solr_cluster_id "id"
                                            :cluster_name "cluster-name"}])
                rnr/list-configs (fn [_ _] [{:config-name "config-name"}])
                rnr/list-collections (fn [_ _] [{:collection-name
                                                 "collection-name"}])
                common/prompt-user-yn (fn [_] true)
                rnr/delete-cluster (fn [_ _])
                persist/write-state (fn [_])]
    (is (= (str "This cluster contains 1 Solr configuration(s) and "
                "1 collection(s)." new-line)
           (with-out-str
             (is (= (str new-line "Cluster 'cluster-name' has been deleted"
                         " from 'rnr'." new-line)
                    (sut/delete-command
                     {:services {:rnr {:type "retrieve_and_rank"}}
                      :user-selections
                      {:retrieve_and_rank "rnr"
                       :cluster {:service-key :rnr
                                 :solr_cluster_id "id"
                                 :cluster_name "cluster-name"}}}
                     ["delete" "cluster" "cluster-name"]
                     []))))))))

(deftest delete-cluster-not-selected
  (with-redefs [rnr/list-clusters (fn [_] [{:solr_cluster_id "id"
                                            :cluster_name "cluster-one"}
                                           {:solr_cluster_id "id"
                                            :cluster_name "cluster-two"}])
                rnr/list-configs (fn [_ _] [])
                rnr/list-collections (fn [_ _] [])
                rnr/delete-cluster (fn [_ _] nil)
                persist/write-state (fn [_] (throw+
                                             "No state change expected."))]
    (is (= (str new-line "Cluster 'cluster-two' has been deleted from 'rnr'."
                new-line)
           (sut/delete-command {:services {:rnr {:type "retrieve_and_rank"}}
                                :user-selections
                                {:retrieve_and_rank "rnr"
                                 :cluster {:service-key :rnr
                                           :solr_cluster_id "id"
                                           :cluster_name "cluster-one"}}}
                               ["delete" "cluster" "cluster-two"]
                               [])))))

(deftest delete-cluster-selected
  (let [output-state (atom {})]
    (with-redefs [rnr/list-clusters (fn [_] [{:solr_cluster_id "id"
                                              :cluster_name "cluster-name"}])
                  rnr/list-configs (fn [_ _] [])
                  rnr/list-collections (fn [_ _] [])
                  rnr/delete-cluster (fn [_ _] nil)
                  persist/write-state (fn [state] (reset! output-state state))]
      (is (= (str new-line "Cluster 'cluster-name' has been deleted from 'rnr'."
                  new-line)
             (sut/delete-command {:services {:rnr {:type "retrieve_and_rank"}}
                                  :user-selections
                                  {:retrieve_and_rank "rnr"
                                   :cluster {:service-key :rnr
                                             :solr_cluster_id "id"
                                             :cluster_name "cluster-name"}}}
                                 ["delete" "cluster" "cluster-name"]
                                 [])))
      (is (= {:services {:rnr {:type "retrieve_and_rank"}}
              :user-selections {:retrieve_and_rank "rnr"}}
             @output-state)))))

(deftest delete-config-missing-name
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"Please specify the name of the Solr configuration to delete."
       (sut/delete-command {} ["delete" "config"] []))))

(deftest delete-config-no-service
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"Couldn't determine which cluster to delete the configuration from."
       (sut/delete-command {} ["delete" "config" "config-name"] []))))

(deftest delete-config-not-selected
  (with-redefs [rnr/delete-config (fn [_ _ _] nil)
                persist/write-state (fn [_] (throw+
                                             "No state change expected."))]
    (is (= (str new-line "Solr configuration 'this-config' has been deleted "
                "from 'rnr/my-cluster'." new-line)
           (sut/delete-command {:services {:rnr {:type "retrieve_and_rank"}}
                                :user-selections
                                {:retrieve_and_rank "rnr"
                                 :config {:service-key :rnr
                                          :cluster-id "id"
                                          :config-name "other-config"}
                                 :cluster {:service-key :rnr
                                           :solr_cluster_id "id"
                                           :cluster_name "my-cluster"}}}
                               ["delete" "config" "this-config"]
                               [])))))

(deftest delete-config-selected
  (let [output-state (atom {})]
    (with-redefs [rnr/delete-config (fn [_ _ _] nil)
                  persist/write-state (fn [state] (reset! output-state state))]
      (is (= (str new-line "Solr configuration 'this-config' has been deleted "
                  "from 'rnr/my-cluster'." new-line)
             (sut/delete-command {:services {:rnr {:type "retrieve_and_rank"}}
                                  :user-selections
                                  {:retrieve_and_rank "rnr"
                                   :config {:service-key :rnr
                                            :cluster-id "id"
                                            :config-name "this-config"}
                                   :cluster {:service-key :rnr
                                             :solr_cluster_id "id"
                                             :cluster_name "my-cluster"}}}
                                 ["delete" "config" "this-config"]
                                 [])))
      (is (= {:services {:rnr {:type "retrieve_and_rank"}}
              :user-selections {:retrieve_and_rank "rnr"
                                :cluster {:service-key :rnr
                                          :solr_cluster_id "id"
                                          :cluster_name "my-cluster"}}}
             @output-state)))))

(def sample-cluster
  {:cluster
   {:service-key "rnr-service"
    :solr_cluster_id "CLUSTER-ID"
    :cluster_name "cluster"
    :cluster_size 1}})

(deftest delete-collection-missing-name
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"Please specify the name of the collection to delete."
       (sut/delete-command {} ["delete" "collection"] []))))

(deftest delete-collection-no-cluster
  (with-redefs [rnr/list-clusters (fn [_] nil)]
    (is (thrown+-with-msg?
         [:type :kale.common/fail]
         #"Couldn't determine which cluster to delete the collection from."
         (sut/delete-command {}
                             ["delete" "collection" "doomed-collection"]
                             [])))))

(deftest delete-collection-not-selected
  (let [output-state (atom {})]
    (with-redefs [rnr/list-collections (fn [_ _] ["doomed-collection"])
                  rnr/delete-collection (fn [_ _ _] nil)
                  persist/write-state (fn [state] (reset! output-state state))]
      (is (= (str new-line "Collection 'doomed-collection' has been deleted "
                  "from 'rnr-service/cluster'." new-line)
             (sut/delete-command
              {:services {:rnr-service {:type "retrieve_and_rank"}}
               :user-selections sample-cluster}
              ["delete" "collection" "doomed-collection"]
              [])))
      (is (= {} @output-state)))))

(deftest delete-collection-selected
  (let [output-state (atom {})]
    (with-redefs [rnr/list-collections (fn [_ _] ["doomed-collection"])
                  rnr/delete-collection (fn [_ _ _] nil)
                  persist/write-state (fn [state] (reset! output-state state))]
      (is (= (str new-line "Collection 'doomed-collection' has been deleted "
                  "from 'rnr-service/cluster'." new-line)
             (sut/delete-command
              {:services {:rnr-service {:type "retrieve_and_rank"}}
               :user-selections
               (merge sample-cluster
                      {:collection
                       {:collection-name "doomed-collection"}})}
              ["delete" "collection" "doomed-collection"]
              [])))
      (is (= {:services {:rnr-service {:type "retrieve_and_rank"}}
              :user-selections sample-cluster}
             @output-state)))))
