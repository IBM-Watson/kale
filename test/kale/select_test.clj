;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.select-test
  (:require [kale.select :as sut]
            [kale.update :refer [update-org-space]]
            [kale.persistence :as persist]
            [kale.getter :refer [rnr-service conversion-service]]
            [kale.cloud-foundry :as cf]
            [kale.cloud-foundry-constants :refer
              [spaces-response orgs-response entry1 entry2]]
            [kale.common :as common :refer [new-line]]
            [clojure.test :as t :refer [deftest is]]
            [clojure.java.io :as io]
            [slingshot.test :refer :all]
            [kale.retrieve-and-rank :as rnr]))

(def default-state {:login {:cf-token "TOKEN"
                            :endpoint "URL"}
                    :services entry1
                    :org-space {:org "org-name"
                                :space "space-name"
                                :guid {:org "ORG_GUID",
                                       :space "SPACE_GUID"}}})

(t/deftest unknown-select-target
  (t/is (thrown+-with-msg?
         [:type :kale.common/fail]
         #"Don't know how to 'select junk'"
         (sut/select {} ["select" "junk"] []))))

(def json-re
  #"Please give the name of a file that contains conversion configuration JSON")

(t/deftest select-convert-config-no-filename
  (t/is (thrown+-with-msg?
         [:type :kale.common/fail]
         json-re
         (sut/select {} ["select" "convert-config"] []))))

(t/deftest select-convert-config-missing-file
  (t/is (thrown+-with-msg?
         [:type :kale.common/fail]
         #"Cannot read the file 'missing-file.json'."
         (sut/select {} ["select" "convert-config" "missing-file.json"] []))))

(t/deftest select-convert-config-not-json
  (spit "broken-convert.json" "{\"conversion_target\":\"BROKEN")
  (t/is (thrown+-with-msg?
         [:type :kale.common/fail]
         #"The contents of 'broken-convert.json' is not JSON."
         (sut/select {} ["select" "convert-config" "broken-convert.json"] []))))

(t/deftest select-convert-config
  (spit "test-convert.json" "{\"conversion_target\":\"NORMALIZED_HTML\"}")
  (let [output-state (atom {})]
    (with-redefs [persist/write-state (fn [state] (reset! output-state state))]
      (t/is (and (= (str "Conversion configuration is now set "
                         "to 'test-convert.json'.")
                    (sut/select {}
                                ["select" "convert-config" "test-convert.json"]
                                []))
                 (= {:user-selections {:conversion-configuration-file
                                       "test-convert.json"}}
                    @output-state))))))

(t/deftest select-org-missing-org
  (t/is (thrown+-with-msg?
         [:type :kale.common/fail]
         #"Please specify an org to change to."
         (sut/select default-state ["select" "org"] []))))

(t/deftest select-org-bad-org
  (with-redefs [cf/get-organizations (fn [_] (orgs-response :resources))]
    (t/is (thrown+-with-msg?
           [:type :kale.common/fail]
           #"Unable to locate org 'bad-org'."
           (sut/select default-state ["select" "org" "bad-org"] [])))))

(t/deftest select-org-get-org
  (let [captured-org-guid (atom {})
        captured-space-guid (atom {})]
    (with-redefs [cf/get-organizations (fn [_] (orgs-response :resources))
                  cf/get-spaces (fn [_ _] (spaces-response :resources))
                  update-org-space
                  (fn [_ org-guid _ space-guid _]
                    (reset! captured-org-guid org-guid)
                    (reset! captured-space-guid space-guid))]
      (t/is (= (str "Switched to using org 'org1'." new-line
                    "Switched to using space 'space1'." new-line
                    "Other space(s) in this org include [space2].")
               (sut/select default-state ["select" "org" "org1"] [])))
      (t/is (and
             (= @captured-org-guid "ORG_GUID1")
             (= @captured-space-guid "SPACE_GUID1"))))))

(t/deftest select-space-missing-space
  (t/is (thrown+-with-msg?
         [:type :kale.common/fail]
         #"Please specify a space to change to."
         (sut/select default-state ["select" "space"] []))))

(t/deftest select-space-bad-space
  (with-redefs [cf/get-spaces (fn [_ _] (spaces-response :resources))]
    (t/is (thrown+-with-msg?
           [:type :kale.common/fail]
           #"Unable to locate space 'bad-space'."
           (sut/select default-state ["select" "space" "bad-space"] [])))))

(t/deftest select-space-get-space
  (let [captured-guid (atom {})]
    (with-redefs [cf/get-spaces (fn [_ _] (spaces-response :resources))
                  update-org-space
                  (fn [_ _ _ guid _] (reset! captured-guid guid))]
      (t/is (= "Switched to using space 'space1'."
               (sut/select default-state ["select" "space" "space1"] [])))
      (t/is (= @captured-guid "SPACE_GUID1")))))

(def state-with-services
  {:services
    {:serviceA {:type "service"}
     :serviceB {:type "service"}}})

(deftest select-unknown-service
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"No service named 'serviceA' was found."
       (sut/service-selector {} :service (fn [_] nil) "serviceA"))))

(deftest select-service-bad-type
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"'serviceA' is a service service, not a bad-service service."
       (sut/service-selector state-with-services
                             :bad-service
                             (fn [_] nil)
                             "serviceA"))))

(deftest select-service-vague
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"Couldn't figure out a default service service to use."
       (sut/service-selector state-with-services
                             :service
                             (fn [_] nil)
                             nil))))

(deftest select-config-match-given-name
  (let [output-state (atom {})]
    (with-redefs [persist/write-state (fn [state] (reset! output-state state))]
      (is (= "You have selected 'serviceA' as your current service service."
             (sut/service-selector state-with-services
                                   :service
                                   (fn [_] nil)
                                   "serviceA")))
      (is (= "serviceA"
             (-> @output-state :user-selections :service))))))

(deftest select-config-use-default
  (let [output-state (atom {})]
    (with-redefs [persist/write-state (fn [state] (reset! output-state state))]
      (is (= "You have selected 'serviceA' as your current service service."
             (sut/service-selector state-with-services
                                   :service
                                   (fn [_] ["serviceA"])
                                   nil)))
      (is (= "serviceA"
             (-> @output-state :user-selections :service))))))

(deftest select-rnr-service
  (with-redefs [sut/service-selector
                 (fn [_ type-key getter _]
                   (is (= :retrieve_and_rank type-key))
                   (is (= getter rnr-service)))]
    (sut/select {} ["select" "rnr"] [])))

(deftest select-dc-service
  (with-redefs [sut/service-selector
                 (fn [_ type-key getter _]
                   (is (= :document_conversion type-key))
                   (is (= getter conversion-service)))]
    (sut/select {} ["select" "dc"] [])))

(deftest select-cluster-no-service
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"Please select or create a retrieve_and_rank service."
       (sut/select {} ["select" "cluster"] []))))

(deftest select-cluster-no-clusters
  (with-redefs [rnr/list-clusters (fn [_] [])]
    (is (thrown+-with-msg?
         [:type :kale.common/fail]
         #"No Solr clusters found in 'rnr'"
         (sut/select {:services {:rnr {:type "retrieve_and_rank"}}}
                     ["select" "cluster"]
                     [])))))

(deftest select-cluster-multiple-clusters
  (with-redefs [rnr/list-clusters (fn [_] [{:cluster_name "CLUSTER-ONE"}
                                           {:cluster_name "CLUSTER-TWO"}])]
    (is (thrown+-with-msg?
         [:type :kale.common/fail]
         #"Please select a cluster to use."
         (sut/select {:services {:rnr {:type "retrieve_and_rank"}}}
                     ["select" "cluster"]
                     [])))))

(deftest select-cluster-no-such-name
  (with-redefs [rnr/list-clusters (fn [_] [{:cluster_name "ONE-CLUSTER"}])]
    (is (thrown+-with-msg?
         [:type :kale.common/fail]
         #"No Solr cluster named 'OTHER-CLUSTER' found in 'rnr'."
         (sut/select {:services {:rnr {:type "retrieve_and_rank"}}}
                     ["select" "cluster" "OTHER-CLUSTER"]
                     [])))))

(deftest select-cluster-same-names
  (with-redefs [rnr/list-clusters (fn [_] [{:cluster_name "CLUSTER-ONE"}
                                           {:cluster_name "CLUSTER-ONE"}])]
    (is (thrown+-with-msg?
         [:type :kale.common/fail]
         #"There are 2 with the name 'CLUSTER-ONE'."
         (sut/select {:services {:rnr {:type "retrieve_and_rank"}}}
                     ["select" "cluster" "CLUSTER-ONE"]
                     [])))))

(deftest select-cluster-pick-one
  (let [output-state (atom {})]
    (with-redefs [persist/write-state (fn [state] (reset! output-state state))
                  rnr/list-clusters (fn [_] [{:cluster_name "CLUSTER-ONE"}
                                             {:cluster_name "CLUSTER-TWO"}])]
      (is (= "You have selected 'CLUSTER-ONE' as your current Solr cluster."
             (sut/select {:services {:rnr {:type "retrieve_and_rank"}}}
                         ["select" "cluster" "CLUSTER-ONE"]
                         [])))
      (is (= {:cluster {:service-key "rnr"
                        :cluster_name "CLUSTER-ONE"}}
             (:user-selections @output-state))))))

(deftest select-cluster-default-one
  (let [output-state (atom {})]
    (with-redefs [persist/write-state (fn [state] (reset! output-state state))
                  rnr/list-clusters (fn [_] [{:cluster_name "THE-CLUSTER"}])]
      (is (= "You have selected 'THE-CLUSTER' as your current Solr cluster."
             (sut/select {:services {:rnr {:type "retrieve_and_rank"}}}
                         ["select" "cluster"]
                         [])))
      (is (= {:cluster {:service-key "rnr"
                        :cluster_name "THE-CLUSTER"}}
             (:user-selections @output-state))))))

(def state-with-cluster
  {:user-selections
    {:cluster
      {:service-key "some-service"
       :solr_cluster_id "CLUSTER-ID"
       :cluster_name "THE-CLUSTER"}}})

(deftest select-config-no-cluster
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"Please select or create a retrieve_and_rank cluster."
       (sut/select {} ["select" "config"] []))))

(deftest select-config-no-configs
  (with-redefs [rnr/list-configs (fn [_ _] nil)]
    (is (thrown+-with-msg?
         [:type :kale.common/fail]
         #"No Solr configurations found in 'THE-CLUSTER'."
         (sut/select state-with-cluster ["select" "config"] [])))))

(deftest select-config-no-match
  (with-redefs [rnr/list-configs (fn [_ _] ["configA"])]
    (is (thrown+-with-msg?
         [:type :kale.common/fail]
         (re-pattern (str "No Solr configurations named 'config' found in"
                          " 'some-service/THE-CLUSTER'."))
         (sut/select state-with-cluster ["select" "config" "config"] [])))))

(deftest select-config-multiple-configs
  (with-redefs [rnr/list-configs (fn [_ _] ["configA" "configB"])]
    (is (thrown+-with-msg?
         [:type :kale.common/fail]
         #"Please select a Solr configuration to use."
         (sut/select state-with-cluster ["select" "config"] [])))))

(deftest select-config-match
  (let [output-state (atom {})]
    (with-redefs [persist/write-state (fn [state] (reset! output-state state))
                  rnr/list-configs (fn [_ _] ["configA" "configB"])]
      (is (= "You have selected 'configB' as your current Solr configuration."
             (sut/select state-with-cluster
                         ["select" "config" "configB"]
                         [])))
      (is (= {:service-key "some-service"
              :cluster-id "CLUSTER-ID"
              :config-name "configB"}
             (-> @output-state :user-selections :config))))))

(deftest select-collection-no-cluster
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"Please select or create a retrieve_and_rank cluster."
       (sut/select {} ["select" "collection"] []))))

(deftest select-collection-no-collections
  (with-redefs [rnr/list-collections (fn [_ _] nil)]
    (is (thrown+-with-msg?
         [:type :kale.common/fail]
         #"No Solr collections found in 'some-service/THE-CLUSTER'."
         (sut/select state-with-cluster ["select" "collection"] [])))))

(deftest select-collection-multiple-collections
  (with-redefs [rnr/list-collections (fn [_ _] ["collectionA" "collectionB"])]
    (is (thrown+-with-msg?
         [:type :kale.common/fail]
         #"Please select a collection to use."
         (sut/select state-with-cluster ["select" "collection"] [])))))

(deftest select-collection-single-collection
  (let [output-state (atom {})]
    (with-redefs [persist/write-state (fn [state] (reset! output-state state))
                  rnr/list-collections (fn [_ _] ["collectionA"])]
      (is (= "You have selected 'collectionA' as your current Solr collection."
             (sut/select state-with-cluster ["select" "collection"] [])))
      (is (= {:service-key "some-service"
              :cluster-id "CLUSTER-ID"
              :cluster-name "THE-CLUSTER"
              :collection-name "collectionA"}
             (-> @output-state :user-selections :collection))))))

(deftest select-collection-no-match
  (with-redefs [rnr/list-collections (fn [_ _] ["collectionA" "collectionB"])]
    (is (thrown+-with-msg?
         [:type :kale.common/fail]
         (re-pattern (str "No Solr collection named 'collectionC' found in "
                          "'some-service/THE-CLUSTER'."))
         (sut/select state-with-cluster
                     ["select" "collection" "collectionC"]
                     [])))))

(deftest select-collection-match
  (let [output-state (atom {})]
    (with-redefs [persist/write-state (fn [state] (reset! output-state state))
                  rnr/list-collections (fn [_ _] ["collectionA" "collectionB"])]
      (is (= "You have selected 'collectionB' as your current Solr collection."
             (sut/select state-with-cluster
                         ["select" "collection" "collectionB"]
                         [])))
      (is (= {:service-key "some-service"
              :cluster-id "CLUSTER-ID"
              :cluster-name "THE-CLUSTER"
              :collection-name "collectionB"}
             (-> @output-state :user-selections :collection))))))
