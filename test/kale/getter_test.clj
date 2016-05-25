;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.getter-test
  (:require [kale.getter :as my]
            [kale.common :refer [set-language]]
            [kale.retrieve-and-rank :as rnr]
            [clojure.test :refer :all]))

(set-language :en)

(deftest default-index-document-version
  (is (= "2016-03-18"
         (my/index-document-version {}))))

(deftest user-set-index-document-version
  (is (= "2016-03-20"
         (my/index-document-version
          {:user-selections {:index-document-version "2016-03-20"}}))))

(deftest no-conversion-configuration-file
  (is (= nil (my/conversion-configuration-file {}))))

(deftest user-set-conversion-configuration-file
  (is (= "conversion.json"
         (my/conversion-configuration-file
          {:user-selections
           {:conversion-configuration-file "conversion.json"}}))))

(deftest no-conversion-service
  (is (= nil
         (my/conversion-service
          {:services {:other-1 {:guid "1234"
                                :type "other"
                                :credentials nil}}}))))

(deftest just-one-conversion-service
  (is (= [:dc-1 {:guid "1234"
                 :type "document_conversion"
                 :credentials nil}]
         (my/conversion-service
          {:services {:dc-1 {:guid "1234"
                             :type "document_conversion"
                             :credentials nil}}}))))

(deftest multiple-conversion-services
  (is (= nil
         (my/conversion-service
          {:services {:dc-1 {:guid "1234"
                             :type "document_conversion"
                             :credentials nil}
                      :dc-2 {:guid "5678"
                             :type "document_conversion"
                             :credentials nil}}}))))

(deftest user-set-conversion-service
  (is (= [:dc-1 {:guid "1234"
                 :type "document_conversion"
                 :credentials nil}]
         (my/conversion-service
          {:services {:dc-1 {:guid "1234"
                             :type "document_conversion"
                             :credentials nil}
                      :dc-2 {:guid "5678"
                             :type "document_conversion"
                             :credentials nil}}
           :user-selections {:document_conversion "dc-1"}}))))

(deftest no-rnr-service
  (is (= nil
         (my/rnr-service
          {:services {:other-1 {:guid "1234"
                                :type "other"
                                :credentials nil}}}))))

(deftest just-one-rnr-service
  (is (= [:rnr-1 {:guid "1234"
                  :type "retrieve_and_rank"
                  :credentials nil}]
         (my/rnr-service
          {:services {:rnr-1 {:guid "1234"
                              :type "retrieve_and_rank"
                              :credentials nil}}}))))

(deftest multiple-rnr-services
  (is (= nil
         (my/rnr-service
          {:services {:rnr-1 {:guid "1234"
                              :type "retrieve_and_rank"
                              :credentials nil}
                      :rnr-2 {:guid "5678"
                              :type "retrieve_and_rank"
                              :credentials nil}}}))))

(deftest user-set-rnr-service
  (is (= [:rnr-1 {:guid "1234"
                  :type "retrieve_and_rank"
                  :credentials nil}]
         (my/rnr-service
          {:services {:rnr-1 {:guid "1234"
                              :type "retrieve_and_rank"
                              :credentials nil}
                      :rnr-2 {:guid "5678"
                              :type "retrieve_and_rank"
                              :credentials nil}}
           :user-selections {:retrieve_and_rank "rnr-1"}}))))

(deftest no-cluster
  (is (= nil
         (my/cluster {}))))

(def cluster-1
  {:solr_cluster_id "fake-id-one",
   :cluster_name "fake-name-one",
   :cluster_size "1",
   :solr_cluster_status "READY"})

(def cluster-2
  {:solr_cluster_id "fake-id-two",
   :cluster_name "fake-name-two",
   :cluster_size "4",
   :solr_cluster_status "READY"})

(deftest user-set-cluster
  (is (= {:service-key :rnr-7,
          :solr_cluster_id "fake-id",
          :cluster_name "fake-name"}
         (my/cluster
          {:user-selections {:cluster {:service-key :rnr-7,
                                       :solr_cluster_id "fake-id",
                                       :cluster_name "fake-name"}}}))))

(deftest just-one-cluster
  (with-redefs [rnr/list-clusters (fn [_] [cluster-1])]
    (is (= (merge {:service-key :rnr-1} cluster-1)
           (my/cluster {:services {:rnr-1 {:guid "1234"
                                           :type "retrieve_and_rank"
                                           :credentials nil}}})))))

(deftest multiple-clusters
  (with-redefs [rnr/list-clusters (fn [_] [cluster-1 cluster-2])]
    (is (= nil
           (my/cluster {:services {:rnr-1 {:guid "1234"
                                           :type "retrieve_and_rank"
                                           :credentials nil}}})))))

(deftest no-collection
  (is (= nil
         (my/collection {}))))

(deftest user-set-collection
  (is (= {:service-key :rnr-7,
          :cluster-id "fake-id",
          :cluster-name "fake-name",
          :collection-name "collection-9"}
         (my/collection
          {:user-selections {:collection {:service-key :rnr-7,
                                          :cluster-id "fake-id",
                                          :cluster-name "fake-name",
                                          :collection-name "collection-9"}}}))))

(deftest just-one-collection
  (with-redefs [rnr/list-clusters (fn [_] [cluster-1])
                rnr/list-collections (fn [_ _] ["collection-1"])]
    (is (= {:service-key :rnr-1,
            :cluster-id "fake-id-one",
            :cluster-name "fake-name-one",
            :collection-name "collection-1"}
           (my/collection {:services {:rnr-1 {:guid "1234"
                                              :type "retrieve_and_rank"
                                              :credentials nil}}})))))

(deftest multiple-collections
  (with-redefs [rnr/list-clusters (fn [_] [cluster-1])
                rnr/list-collections (fn [_ _] ["collection-1" "collection-2"])]
    (is (= nil
           (my/collection {:services {:rnr-1 {:guid "1234"
                                              :type "retrieve_and_rank"
                                              :credentials nil}}})))))

(deftest no-solr-configuration
  (is (= nil
         (my/solr-configuration {}))))

(deftest user-set-solr-configuration
  (is (= {:service-key :rnr-7,
          :cluster-id "fake-id",
          :cluster-name "fake-name",
          :config-name "config-9"}
         (my/solr-configuration
          {:user-selections {:config {:service-key :rnr-7,
                                      :cluster-id "fake-id",
                                      :cluster-name "fake-name",
                                      :config-name "config-9"}}}))))

(deftest just-one-solr-configuration
  (with-redefs [rnr/list-clusters (fn [_] [cluster-1])
                rnr/list-configs (fn [_ _] ["config-1"])]
    (is (= {:service-key :rnr-1,
            :cluster-id "fake-id-one",
            :config-name "config-1"}
           (my/solr-configuration {:services {:rnr-1 {:guid "1234"
                                                      :type "retrieve_and_rank"
                                                      :credentials nil}}})))))

(deftest multiple-solr-configurations
  (with-redefs [rnr/list-clusters (fn [_] [cluster-1])
                rnr/list-configs (fn [_ _] ["config-1" "config-2"])]
    (is (= nil
           (my/solr-configuration {:services {:rnr-1 {:guid "1234"
                                                      :type "retrieve_and_rank"
                                                      :credentials nil}}})))))
