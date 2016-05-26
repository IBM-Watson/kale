;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.get-command-test
  (:require [kale.get-command :as sut]
            [kale.common :refer [set-language new-line]]
            [clojure.test :refer [deftest is]]
            [slingshot.slingshot :refer [throw+]]
            [slingshot.test :refer :all]
            [kale.retrieve-and-rank :as rnr]))

(set-language :en)

(deftest unknown-get-target
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"Don't know how to 'get junk'"
       (sut/get-command {} ["get" "junk"] []))))

(deftest get-config-missing-name
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"Please specify a Solr configuration name to download."
       (sut/get-command {} ["get" "config"] []))))

(deftest get-config-no-cluster
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"Please select or create a cluster to work with."
       (sut/get-command {} ["get" "config" "my-conf"] []))))

(deftest get-config-not-found
  (with-redefs [rnr/download-config (fn [_ _ _] (throw+ {:status 404}))]
    (is (thrown+-with-msg?
         [:type :kale.common/fail]
         #"A Solr configuration named 'my-conf' does not exist"
         (sut/get-command
          {:services {:rnr {:type "retrieve_and_rank"}}
           :user-selections {:retrieve_and_rank "rnr"
                             :cluster {:service-key :rnr
                                       :solr_cluster_id "id"
                                       :cluster_name "cluster-name"
                                       :cluster_size 1}}}
          ["get" "config" "my-conf"]
          [])))))

(deftest get-config-success
  (with-redefs [rnr/download-config (fn [_ _ _] (byte-array [65 66 67 68 69]))]
    (is (= (str new-line "Configuration saved into 'fake-conf.zip'." new-line)
           (sut/get-command {:services {:rnr {:type "retrieve_and_rank"}}
                             :user-selections
                             {:retrieve_and_rank "rnr"
                              :cluster {:service-key :rnr
                                        :solr_cluster_id "id"
                                        :cluster_name "cluster-name"
                                        :cluster_size 1}}}
                            ["get" "config" "fake-conf"]
                            [])))))

(deftest get-config-selection-success
  (with-redefs [rnr/download-config (fn [_ _ _] (byte-array [65 66 67 68 69]))]
    (is (= (str new-line "Configuration saved into 'fake-conf.zip'." new-line)
           (sut/get-command {:services {:rnr {:type "retrieve_and_rank"}}
                             :user-selections
                             {:retrieve_and_rank "rnr"
                              :cluster {:service-key :rnr
                                        :solr_cluster_id "id"
                                        :cluster_name "cluster-name"
                                        :cluster_size 1}
                              :config {:service-key :rnr
                                       :cluster-id "id"
                                       :config-name "fake-conf"}}}
                            ["get" "config"]
                            [])))))
