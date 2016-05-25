;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.create-test
  (:require [kale.create :as sut]
            [kale.persistence :as persist]
            [kale.cloud-foundry :as cf]
            [kale.cloud-foundry-constants :as c]
            [cheshire.core :as json]
            [clj-http.fake :refer [with-fake-routes-in-isolation]]
            [kale.common :refer [new-line] :as common]
            [clojure.test :refer [deftest is]]
            [slingshot.test :refer :all]
            [kale.getter :as my]
            [kale.retrieve-and-rank :as rnr]))

(common/set-language :en)

(def default-state {:login {:cf-token "TOKEN"
                            :endpoint "URL"}
                    :services c/entry1
                    :org-space {:org "org-name"
                                :space "space-name"
                                :guid {:org "ORG_GUID",
                                       :space "SPACE_GUID"}}})

(deftest unknown-create-target
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"Don't know how to 'create junk'"
       (sut/create {} ["create" "junk"] []))))

(deftest create-space-no-name-specified
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"Please specify a name for the space."
       (sut/create {} ["create" "space"] []))))

(deftest create-space-indeed
  (let [output-state (atom {})]
    (with-redefs [cf/get-spaces (fn [_ _] (c/spaces-response :resources))
                  cf/get-user-guid (fn [_] "USER_GUID")
                  cf/create-space
                  (fn [_ _ _ _] (c/space-entity "NEW_GUID" "new-space"))
                  persist/write-state (fn [state] (reset! output-state state))]
      (is (= (str "Space 'new-space' has been created"
                  " and selected for future actions.")
             (sut/create default-state
                         ["create" "space" "new-space"] [])))
      (is (= {:login {:cf-token "TOKEN"
                      :endpoint "URL"}
              :services {}
              :org-space
              {:org "org-name"
               :space "new-space"
               :guid
               {:org "ORG_GUID"
                :space "NEW_GUID"}}}
             @output-state)))))

(deftest create-dc-service
  (with-redefs [sut/create-service-with-key
                (fn [_ service-type service-name _]
                  (is (= "document_conversion" service-type))
                  (is (= "dc-name" service-name)))]
    (sut/create default-state ["create" "dc" "dc-name"] [])))

(deftest create-rnr-service
  (with-redefs [sut/create-service-with-key
                (fn [_ service-type service-name _]
                  (is (= "retrieve_and_rank" service-type))
                  (is (= "rnr-name" service-name)))]
    (sut/create default-state ["create" "rnr" "rnr-name"] [])))

(deftest create-service-no-name-specified
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"Please specify a name for the service."
       (sut/create-service-with-key default-state "service-type" nil false))))

(deftest wait-for-service-success
  (let [counter (atom 0)]
    (with-redefs [cf/get-service-status
                  (fn [_ _]
                    (swap! counter inc)
                    (if (= @counter 3) "create succeeded"
                                       "create in progress"))]
      (is (= (str "..." new-line)
             (with-out-str
               (sut/wait-for-service c/cf-auth "GUID")))))))

(deftest wait-for-service-fail
  (with-redefs [cf/get-service-status (fn [_ _] "create failed")]
    (with-out-str
      (is (thrown+-with-msg?
           [:type :kale.common/fail]
           #"Service creation failed."
           (sut/wait-for-service c/cf-auth "GUID"))))))

(def service-instance
  (c/service-instance-entity "GUID" "service-name"))

(def service-key
  (c/service-key-entity "GUID" "retrieve_and_rank"))

(deftest create-service-with-existing-plan
  (with-fake-routes-in-isolation
    {(c/cf-url "/v2/service_instances?accepts_incomplete=true")
     (c/respond {:body (json/encode service-instance)})}
    (with-redefs [cf/get-service-plan-guid (fn [_ _ _ _] "PLAN_GUID")]
      (is (= (str "Creating retrieve_and_rank service 'new-service' "
                  "using the 'standard' plan." new-line)
             (with-out-str
               (is (= service-instance
                      (sut/create-service-with-plan
                       c/cf-auth "SPACE_GUID" "retrieve_and_rank"
                                 "new-service" "standard")))))))))

(deftest create-service-with-unknown-plan
  (with-redefs [cf/get-service-plan-guid (fn [_ _ _ _] nil)]
    (is (thrown+-with-msg?
      [:type :kale.common/fail]
      (re-pattern (str "Plan 'enterprise' is not available "
                       "for service type 'retrieve_and_rank' "
                       "in this organization."))
      (sut/create-service-with-plan
        c/cf-auth "SPACE_GUID" "retrieve_and_rank"
                  "new-service" "enterprise")))))

(deftest create-key-for-service
  (with-fake-routes-in-isolation
    {(c/cf-url "/v2/service_keys")
     (c/respond {:body (json/encode service-key)})}
    (is (= (str "Creating key for service 'some-service'." new-line)
           (with-out-str
             (is (= service-key
                    (sut/create-key-for-service
                     c/cf-auth "some-service" "GUID"))))))))

(deftest create-service-instance
  (let [output-state (atom {})]
    (with-redefs [sut/create-service-with-plan (fn [_ _ _ _ _] service-instance)
                  sut/create-key-for-service (fn [_ _ service-guid]
                                               (if (= service-guid "GUID")
                                                 service-key nil))
                  persist/write-state (fn [state] (reset! output-state state))]
      (is (= (str "Service 'new-service' has been created"
                  " and selected for future actions.")
             (sut/create-service-with-key default-state
                                          "retrieve_and_rank"
                                          "new-service"
                                          false)))
      (is (= (merge (update-in
                     default-state [:services] merge
                     {:new-service {:guid "GUID"
                                    :type "retrieve_and_rank"
                                    :plan "standard"
                                    :key-guid "KEY_GUID"
                                    :credentials
                                    (-> service-key :entity :credentials)}})
                    {:user-selections {:retrieve_and_rank "new-service"}})
             @output-state)))))

(deftest create-service-instance-standard
  (with-redefs [sut/create-service-with-plan (fn [_ _ _ _ service-plan]
                                               (is (= service-plan
                                                      "standard"))
                                               service-instance)
                sut/create-key-for-service (fn [_ _ _] service-key)
                persist/write-state (fn [_])]
      (sut/create-service-with-key default-state
                                   "retrieve_and_rank"
                                   "new-service"
                                   false)))

(deftest create-service-instance-enterprise
  (with-redefs [sut/create-service-with-plan (fn [_ _ _ _ service-plan]
                                               (is (= service-plan
                                                      "enterprise"))
                                               service-instance)
                sut/create-key-for-service (fn [_ _ _] service-key)
                sut/wait-for-service (fn [_ _])
                persist/write-state (fn [_])]
      (sut/create-service-with-key default-state
                                   "retrieve_and_rank"
                                   "new-service"
                                   true)))

(deftest create-cluster-missing-name
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"Please specify the name of the cluster to create."
       (sut/create {} ["create" "cluster"] []))))

(deftest create-cluster-bad-size
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"Cluster size must be an integer in the range of 1 to 7."
       (sut/create {}
                   ["create" "cluster" "cluster-name" "not-an-integer"]
                   []))))

(deftest create-cluster-too-small
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"Cluster size must be an integer in the range of 1 to 7."
       (sut/create {} ["create" "cluster" "cluster-name" "0"] []))))

(deftest create-cluster-too-large
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"Cluster size must be an integer in the range of 1 to 7."
       (sut/create {} ["create" "cluster" "cluster-name" "8"] []))))

(deftest create-cluster-no-service
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"Couldn't determine which service to create the cluster in."
       (sut/create {} ["create" "cluster" "cluster-name" nil] []))))

(deftest create-cluster-duplicate-name
  (with-redefs [rnr/list-clusters (fn [_] [{:cluster_name "cluster-name"}])]
    (is (thrown+-with-msg?
         [:type :kale.common/fail]
         #"A cluster named 'cluster-name' already exists."
         (sut/create {:services {:rnr {:type "retrieve_and_rank"}}
                      :user-selections {:retrieve_and_rank "rnr"}}
                     ["create" "cluster" "cluster-name" nil]
                     [])))))

(deftest create-cluster-indeed
  (let [output-state (atom {})]
    (with-redefs [rnr/list-clusters (fn [_] [])
                  rnr/create-cluster (fn [_ n s] {:solr_cluster_id "id"
                                                  :cluster_name n
                                                  :cluster_size s})
                  persist/write-state (fn [state] (reset! output-state state))]
      (is (= (str "Creating cluster 'cluster-name' in 'rnr'." new-line)
             (with-out-str
               (is (= (str "Cluster 'cluster-name' has been created"
                           " and selected for future actions." new-line
                           "It will take a few minutes to become available.")
                      (sut/create {:services {:rnr {:type "retrieve_and_rank"}}
                                   :user-selections {:retrieve_and_rank "rnr"}}
                                  ["create" "cluster" "cluster-name" 1]
                                  []))))))
      (is (= {:services {:rnr {:type "retrieve_and_rank"}}
              :user-selections {:retrieve_and_rank "rnr"
                                :cluster {:service-key "rnr"
                                          :solr_cluster_id "id"
                                          :cluster_name "cluster-name"
                                          :cluster_size 1}}}
             @output-state)))))

(deftest create-config-missing-name
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"Please specify the name of the Solr configuration to create."
       (sut/create {} ["create" "config"] []))))

(deftest create-config-no-zip-file-specified
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"'my-conf' is not a prepackaged Solr configuration."
       (sut/create {} ["create" "config" "my-conf"] []))))

(deftest create-config-missing-zip-file
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"Cannot read the file named 'no-such-config.zip'."
       (sut/create {} ["create" "config" "my-conf" "no-such-config.zip"] []))))

(deftest create-config-no-cluster
  (with-redefs [common/readable-files? (fn [_] true)]
    (is (thrown+-with-msg?
         [:type :kale.common/fail]
         #"Couldn't determine which cluster to create the configuration in."
         (sut/create {} ["create" "config" "my-conf" "test-config.zip"] [])))))

(deftest create-config-from-prepackaged
  (let [output-state (atom {})]
    (with-redefs [rnr/upload-config (fn [_ _ _ _] nil)
                  persist/write-state (fn [state] (reset! output-state state))]
      (is (= (str "Creating configuration 'english' in 'rnr/cluster-name'."
                  new-line)
             (with-out-str
               (is (= (str "Solr configuration named 'english' has been"
                           " created and selected for future actions.")
                      (sut/create
                        {:services {:rnr {:type "retrieve_and_rank"}}
                         :user-selections
                           {:retrieve_and_rank "rnr"
                            :cluster {:service-key "rnr"
                                      :solr_cluster_id "id"
                                      :cluster_name "cluster-name"
                                      :cluster_size 1}}}
                        ["create" "config" "english"]
                        []))))))
      (is (= {:services {:rnr {:type "retrieve_and_rank"}}
              :user-selections {:retrieve_and_rank "rnr"
                                :config {:service-key "rnr"
                                         :cluster-id "id"
                                         :config-name "english"}
                                :cluster {:service-key "rnr"
                                          :solr_cluster_id "id"
                                          :cluster_name "cluster-name"
                                          :cluster_size 1}}}
             @output-state)))))

(deftest create-config-from-file
  (let [output-state (atom {})]
    (with-redefs [common/readable-files? (fn [_] true)
                  rnr/upload-config (fn [_ _ _ _] nil)
                  persist/write-state (fn [state] (reset! output-state state))]
      (is (= (str "Creating configuration 'my-conf' in 'rnr/cluster-name'."
                  new-line)
             (with-out-str
               (is (= (str "Solr configuration named 'my-conf' has been"
                           " created and selected for future actions.")
                      (sut/create
                        {:services {:rnr {:type "retrieve_and_rank"}}
                         :user-selections
                           {:retrieve_and_rank "rnr"
                            :cluster {:service-key "rnr"
                                      :solr_cluster_id "id"
                                      :cluster_name "cluster-name"
                                      :cluster_size 1}}}
                        ["create" "config" "my-conf" "test-config.zip"]
                        []))))))
      (is (= {:services {:rnr {:type "retrieve_and_rank"}}
              :user-selections {:retrieve_and_rank "rnr"
                                :config {:service-key "rnr"
                                         :cluster-id "id"
                                         :config-name "my-conf"}
                                :cluster {:service-key "rnr"
                                          :solr_cluster_id "id"
                                          :cluster_name "cluster-name"
                                          :cluster_size 1}}}
             @output-state)))))

(def sample-cluster
  {:cluster
   {:service-key "rnr-service"
    :solr_cluster_id "CLUSTER-ID"
    :cluster_name "cluster"
    :cluster_size 1}})

(def sample-config
  {:config
   {:service-key "rnr-service"
    :cluster-id "CLUSTER-ID"
    :cluster-name "cluster"
    :config-name "default-config"}})

(deftest create-collection-no-name-specified
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"Please specify the name of the collection to create."
       (sut/create {} ["create" "collection"] []))))

(deftest create-collection-no-cluster
  (with-redefs [rnr/list-clusters (fn [_] [])
                rnr/list-configs (fn [_ _] [])]
    (is (thrown+-with-msg?
         [:type :kale.common/fail]
         #"Couldn't determine which cluster to create the collection in."
         (sut/create {} ["create" "collection" "new-collection"] [])))))

(deftest create-collection-no-config
  (with-redefs [rnr/list-configs (fn [_ _] [])]
    (is (thrown+-with-msg?
         [:type :kale.common/fail]
         #"Couldn't determine which Solr configuration to use."
         (sut/create {:services {:rnr-service {:type "retrieve_and_rank"}}
                      :user-selections sample-cluster}
                     ["create" "collection" "new-collection"]
                     [])))))

(deftest create-collection-indeed
  (let [output-state (atom {})]
    (with-redefs [rnr/create-collection (fn [_ _ _ _] nil)
                  persist/write-state (fn [state] (reset! output-state state))]
      (is (= (str "Creating collection 'new-collection' in"
                  " 'rnr-service/cluster' using config 'default-config'."
                  new-line)
             (with-out-str
               (is (= (str "Collection 'new-collection' has been created and "
                           "selected for future actions.")
                      (sut/create {:user-selections
                                   (merge sample-cluster
                                          sample-config)}
                                  ["create" "collection" "new-collection"]
                                  []))))))
      (is (= {:user-selections
              (merge sample-cluster
                     sample-config
                     {:collection
                      {:service-key "rnr-service"
                       :cluster-id "CLUSTER-ID"
                       :cluster-name "cluster"
                       :collection-name "new-collection"}})}
             @output-state)))))

(deftest create-crawler-no-collection
  (with-redefs [rnr/list-clusters (fn [_] [])
                rnr/list-collections (fn [_ _] [])]
    (is (thrown+-with-msg?
         [:type :kale.common/fail]
         #"Couldn't determine which collection to tell the crawler to use."
         (sut/create {} ["create" "cc"] [])))))

(deftest create-crawler-no-dc-service
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"Couldn't determine which document_conversion service to tell "
       (sut/create {:user-selections
                    {:collection
                     {:service-key "rnr-service"
                      :cluster-id "CLUSTER-ID"
                      :cluster-name "cluster"
                      :collection-name "new-collection"}}}
                   ["create" "cc"]
                   []))))

(deftest create-crawler-indeed
  (is (= (str "Created two files for setting up the Data Crawler:"
              new-line
              "    'orchestration_service.conf' contains document_conversion"
              " service connection information."
              new-line
              "    'orchestration_service_config.json' contains"
              " configurations sent to the 'index_document' API call.")
         (sut/create {:user-selections
                      {:document_conversion "test-dc"
                       :collection
                       {:service-key "rnr-service"
                        :cluster-id "CLUSTER-ID"
                        :cluster-name "cluster"
                        :collection-name "new-collection"}}}
                     ["create" "cc"]
                     [])))

  (is (= (str "{" new-line
              "  \"http_timeout\" : 600," new-line
              "  \"concurrent_upload_connection_limit\" : 100," new-line
              "  \"base_url\" : null," new-line
              "  \"endpoint\" : \"/v1/index_document?version=2016-03-18\","
              new-line
              "  \"credentials\" : {" new-line
              "    \"username\" : null," new-line
              "    \"password\" : null" new-line
              "  }," new-line
              "  \"config_file\" : \"orchestration_service_config.json\""
              new-line
              "}")
         (slurp "orchestration_service.conf")))

  (is (= (str "{" new-line
              "  \"retrieve_and_rank\" : {" new-line
              "    \"cluster_id\" : \"CLUSTER-ID\"," new-line
              "    \"search_collection\" : \"new-collection\"," new-line
              "    \"service_instance_id\" : null," new-line
              "    \"fields\" : {" new-line
              "      \"include\" : [ \"body\", \"contentHtml\","
              " \"contentText\", \"id\", \"indexedTimestamp\","
              " \"searchText\", \"sourceUrl\", \"title\" ]" new-line
              "    }" new-line
              "  }" new-line
              "}")
         (slurp "orchestration_service_config.json"))))
