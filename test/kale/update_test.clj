;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.update-test
  (:require [kale.update :as sut]
            [kale.cloud-foundry :as cf]
            [kale.cloud-foundry-constants :refer [entry1 entry2]]
            [kale.retrieve-and-rank :as rnr]
            [kale.common :refer [new-line]]
            [kale.persistence :refer [write-state]]
            [clojure.test :refer [deftest is]]
            [slingshot.slingshot :refer [throw+]]
            [slingshot.test :refer :all]))

(deftest child-hierarchy
  (is (= (seq [:retrieve_and_rank :document_conversion
           :cluster :config :collection])
         (sut/get-child-elements :services))))

(deftest missing-selection-rnr-not
  (is (not (sut/missing-selection? :retrieve_and_rank
                                   {:services
                                      {:rnr {:type "retrieve_and_rank"}}
                                    :user-selections
                                      {:retrieve_and_rank "rnr"}}))))

(deftest missing-selection-rnr-indeed
  (is (sut/missing-selection? :retrieve_and_rank
                              {:services {}
                               :user-selections
                                 {:retrieve_and_rank "rnr"}})))

(deftest missing-selection-dc-not
  (is (not (sut/missing-selection? :document_conversion
                                   {:services
                                      {:dc {:type "document_conversion"}}
                                    :user-selections
                                      {:document_conversion "dc"}}))))

(deftest missing-selection-dc-indeed
  (is (sut/missing-selection? :document_conversion
                              {:services {}
                               :user-selections
                                 {:document_conversion "dc"}})))

(deftest missing-selection-cluster-not
  (with-redefs [rnr/list-clusters (fn [_] [{:service-key "rnr"
                                            :solr_cluster_id "CLUSTER-ID"
                                            :cluster_name "some-cluster"
                                            :cluster_size ""}])]
    (is (not (sut/missing-selection?
               :cluster
               {:services {:rnr {:credentials {}}}
                :user-selections
                  {:cluster
                     {:service-key "rnr"
                      :solr_cluster_id "CLUSTER-ID"}}})))))

(deftest missing-selection-cluster-indeed
  (with-redefs [rnr/list-clusters (fn [_] [])]
    (is (sut/missing-selection? :cluster
                                {:services {:rnr {:credentials {}}}
                                 :user-selections
                                   {:cluster
                                      {:service-key "rnr"
                                       :solr_cluster_id "CLUSTER-ID"}}}))))

(deftest missing-selection-config-not
  (with-redefs [rnr/list-configs (fn [_ _] ["some-config"])]
    (is (not (sut/missing-selection?
               :config
               {:services {:rnr {:credentials {}}}
                :user-selections {:config
                                    {:service-key "rnr"
                                     :config-name "some-config"}}})))))

(deftest missing-selection-config-indeed
  (with-redefs [rnr/list-configs (fn [_ _] [])]
    (is (sut/missing-selection?
          :config
          {:services {:rnr {:credentials {}}}
           :user-selections {:config
                              {:service-key "rnr"
                               :config-name "some-config"}}}))))

(deftest missing-selection-collection-not
  (with-redefs [rnr/list-collections (fn [_ _] ["some-collection"])]
    (is (not (sut/missing-selection?
               :collection
               {:services {:rnr {:credentials {}}}
                :user-selections {:collection
                                    {:service-key "rnr"
                                     :collection-name "some-collection"}}})))))

(deftest missing-selection-collection-indeed
  (with-redefs [rnr/list-collections (fn [_ _] [])]
    (is (sut/missing-selection?
          :collection
          {:services {:rnr {:credentials {}}}
           :user-selections {:collection
                              {:service-key "rnr"
                               :collection-name "some-collection"}}}))))

(deftest missing-selection-no-selection
  (is (not (sut/missing-selection? :retrieve_and_rank {}))))

(deftest missing-selection-no-credentials
  (is (sut/missing-selection?
        :collection
        {:services {}
         :user-selections {:collection {:service-key "rnr"}}})))

(deftest missing-selection-http-error
  (with-redefs [rnr/list-collections (fn [_ _] (throw+ {:status 400}))]
    (is (sut/missing-selection?
          :collection
          {:services {:rnr {:credentials {}}}
           :user-selections {:collection
                              {:service-key "rnr"
                               :collection-name "some-collection"}}}))))

(def state-with-selections
  {:services
     {:rnr {:credentials {}}}
   :user-selections
     {:retrieve_and_rank "rnr"
      :cluster {:service-key "rnr"
                :solr_cluster_id "CLUSTER-ID"}
      :config {:service-key "rnr"
               :config-name "some-config"}
      :collection {:service-key "rnr"
                   :collection-name "some-collection"}}})

(deftest list-missing-selections-none
  (with-redefs [sut/missing-cluster? (fn [_ _] false)
                sut/missing-config? (fn [_ _] false)
                sut/missing-collection? (fn [_ _] false)]
    (is (= '()
           (sut/list-missing-selections :services state-with-selections)))))

(deftest list-missing-selections-no-collection
  (with-redefs [sut/missing-cluster? (fn [_ _] false)
                sut/missing-config? (fn [_ _] false)
                sut/missing-collection? (fn [_ _] true)]
    (is (= '(:collection)
           (sut/list-missing-selections :services state-with-selections)))))

(deftest list-missing-selections-no-cluster
  (with-redefs [sut/missing-cluster? (fn [_ _] true)]
    (is (= '(:cluster :config :collection)
           (sut/list-missing-selections :services state-with-selections)))))

(def user-selections
  {:user-selections
    {:conversion-configuration-file "test-convert.json"
     :document_conversion "dc_service"
     :retrieve_and_rank "rnr_service"
     :cluster "some-cluster"
     :config "some-config"
     :collection "some-collection"}})

(def default-state
  (merge
    {:services entry1
     :org-space {:org "org-name"
                 :space "space-name"
                 :guid {:org "ORG_GUID"
                        :space "SPACE_GUID"}}}
    user-selections))

(deftest update-org-space-new-selections
  (let [expected-update {:services entry2
                         :org-space {:org "new-org"
                                     :space "new-space"
                                     :guid {:org "NEW_ORG_GUID"
                                            :space "NEW_SPACE_GUID"}}
                         :user-selections {:conversion-configuration-file
                                           "test-convert.json"}}
        captured-state (atom {})]
    (with-redefs [write-state (fn [state] (reset! captured-state state))
                  cf/get-services (fn [_ _] entry2)]
      (is (= (str "Loading services..." new-line)
             (with-out-str
               (sut/update-org-space "new-org" "NEW_ORG_GUID"
                                     "new-space" "NEW_SPACE_GUID"
                                     default-state))))
      (is (= @captured-state (merge default-state expected-update))))))

(deftest update-org-space-same-selections
  (let [captured-state (atom {})]
    (with-redefs [write-state (fn [state] (reset! captured-state state))
                  cf/get-services (fn [_ _] entry1)
                  sut/list-missing-selections (fn [_ _] [])]
      (is (= (str "Loading services..." new-line)
             (with-out-str
               (sut/update-org-space "org-name" "ORG_GUID"
                                     "space-name" "SPACE_GUID"
                                     default-state))))
      (is (= @captured-state default-state)))))

(deftest update-selection-no-children
  (let [output-state (atom {})]
    (with-redefs [write-state (fn [state] (reset! output-state state))]
      (sut/update-user-selection {:user-selections
                                   {:document_conversion "dc"
                                    :retrieve_and_rank "rnr"
                                    :cluster "cluster"}}
                                 :document_conversion
                                 "new-dc")
      (is (= {:user-selections
               {:document_conversion "new-dc"
                :retrieve_and_rank "rnr"
                :cluster "cluster"}}
             @output-state)))))

(deftest update-selection-children
  (let [output-state (atom {})]
    (with-redefs [write-state (fn [state] (reset! output-state state))]
      (sut/update-user-selection {:user-selections
                                   {:document_conversion "dc"
                                    :retrieve_and_rank "rnr"
                                    :cluster "cluster"}}
                                 :retrieve_and_rank
                                 "new-rnr")
      (is (= {:user-selections
               {:document_conversion "dc"
                :retrieve_and_rank "new-rnr"}}
             @output-state)))))

(deftest update-selection-no-change
  (let [output-state (atom {})]
    (with-redefs [write-state (fn [state] (reset! output-state state))]
      (sut/update-user-selection {:user-selections
                                   {:document_conversion "dc"
                                    :retrieve_and_rank "rnr"
                                    :cluster "cluster"}}
                                 :retrieve_and_rank
                                 "rnr")
      (is (= {:user-selections
               {:document_conversion "dc"
                :retrieve_and_rank "rnr"
                :cluster "cluster"}}
             @output-state)))))

(deftest delete-selection-no-children
  (let [output-state (atom {})]
    (with-redefs [write-state (fn [state] (reset! output-state state))]
      (sut/delete-user-selection {:user-selections
                                   {:document_conversion "dc"
                                    :retrieve_and_rank "rnr"
                                    :cluster "cluster"}}
                                 :document_conversion)
      (is (= {:user-selections
               {:retrieve_and_rank "rnr"
                :cluster "cluster"}}
             @output-state)))))

(deftest delete-selection-children
  (let [output-state (atom {})]
    (with-redefs [write-state (fn [state] (reset! output-state state))]
      (sut/delete-user-selection {:user-selections
                                   {:document_conversion "dc"
                                    :retrieve_and_rank "rnr"
                                    :cluster "cluster"}}
                                 :retrieve_and_rank)
      (is (= {:user-selections
               {:document_conversion "dc"}}
             @output-state)))))
