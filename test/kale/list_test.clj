;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.list-test
  (:require [kale.list :as sut]
            [kale.common :refer [new-line] :as common]
            [kale.cloud-foundry :as cf]
            [kale.retrieve-and-rank :as rnr]
            [kale.cloud-foundry-constants :refer [orgs-response
                                                  spaces-response]]
            [clojure.test :as t]
            [slingshot.slingshot :refer [throw+]]
            [slingshot.test :refer :all]))

(common/set-language :en)

(t/deftest unknown-list-target
  (t/is (thrown+-with-msg?
         [:type :kale.common/fail]
         (re-pattern (str "Don't know how to 'list junk'.*"
                          "Available actions for list:.*"
                          "Or use 'kale list' to list everything."))
         (sut/list-info {} ["list" "junk"] []))))

(t/deftest list-orgs
  (with-redefs [cf/get-organizations (fn [_] (orgs-response :resources))]
    (t/is (= (str "Available organizations:"
             new-line "   org1"
             new-line "   org2"
             new-line new-line
             "Currently using organization 'org1'")
           (sut/list-info {:org-space {:org "org1"}}
                          ["list" "org"]
                          [])))))

(t/deftest list-spaces
  (with-redefs [cf/get-spaces (fn [_ _] (spaces-response :resources))]
    (t/is (= (str "Available spaces in the 'org1' organization:"
                  new-line "   space1"
                  new-line "   space2"
                  new-line new-line
                  "Currently using space 'space1'")
           (sut/list-info {:org-space {:org "org1"
                                       :space "space1"}}
                          ["list" "space"]
                          [])))))

(t/deftest list-no-services
  (t/is (thrown+-with-msg?
         [:type :kale.common/fail]
         #"No services found in the 'dev' space."
         (sut/list-info {:org-space {:space "dev"}
                         :services {}}
                        ["list" "services"]
                        []))))

(t/deftest list-unknown-services
  (with-redefs [cf/get-service-status (fn [_ _] "create succeeded")]
    (t/is (= (str "Available services in the 'dev' space:" new-line
                  "   [standard] cognition service named: magic-1"
                  new-line new-line
                  "   [standard] database service named: simple-1"
                  new-line)
             (sut/list-info {:org-space {:space "dev"}
                             :services {:magic-1 {:type "cognition"
                                                  :plan "standard"
                                                  :credentials {}}
                                        :simple-1 {:type "database"
                                                   :plan "standard"
                                                   :credentials {}}}}
                         ["list" "services"]
                         [])))))

(t/deftest list-service-without-credentials
  (with-redefs [cf/get-service-status (fn [_ _] "create succeeded")]
    (t/is (= (str "Available services in the 'dev' space:" new-line
                  "   [standard] cognition service named: magic-1" new-line
                  "      WARNING: This service has no access credentials."
                  new-line)
             (sut/list-info {:org-space {:space "dev"}
                             :services {:magic-1 {:type "cognition"
                                                  :plan "standard"}}}
                         ["list" "services"]
                         [])))))

(t/deftest ignore-failed-services
  (with-redefs [cf/get-service-status (fn [_ _] "create failed")]
    (t/is (thrown+-with-msg?
           [:type :kale.common/fail]
           #"No services found in the 'dev' space."
           (sut/list-info {:org-space {:space "dev"}
                           :services {:magic-1 {:type "cognition"
                                                :plan "standard"}}}
                          ["list" "services"]
                          [])))))

(t/deftest rnr-status-with-cluster
  (with-redefs [rnr/list-clusters (fn [_] [{:solr_cluster_id "id-1"
                                            :cluster_name "ke-1"
                                            :cluster_size ""
                                            :solr_cluster_status "READY"}])
                rnr/list-configs (fn [_ _] ["config-1" "config-2"])
                rnr/list-collections (fn [_ _] ["collection-1" "collection-2"])]
    (t/is (= (str "Available services in the 'dev' space:" new-line
                  "   [standard] retrieve_and_rank service named: rnr-1"
                  new-line
                  "      Cluster name: ke-1, size: free, status: READY" new-line
                  "         configs: config-1, config-2" new-line
                  "         collections: collection-1, collection-2")
             (sut/list-services {:rnr-1 {:type "retrieve_and_rank"
                                         :plan "standard"
                                         :credentials {:url "http://rnr/api"
                                                       :username "user"
                                                       :password "pwd"}}}
                                "dev"
                                nil
                                nil)))))

(t/deftest rnr-status-with-not-ready-cluster
  (with-redefs [rnr/list-clusters (fn [_]
                                    [{:solr_cluster_id "id-1"
                                      :cluster_name "ke-1"
                                      :cluster_size "4"
                                      :solr_cluster_status "READY"}
                                     {:solr_cluster_id "id-2"
                                      :cluster_name "ke-2"
                                      :cluster_size "2"
                                      :solr_cluster_status "NOT_AVAILABLE"}])
                rnr/list-configs (fn [_ id]
                                   (if (= "id-1" id)
                                     ["config-1" "config-2"]
                                     (throw+ (str "WRRCSS030: Solr cluster ID ["
                                                  id "] does not exist."))))
                rnr/list-collections (fn [_ id]
                                       (if (= "id-1" id)
                                         ["collection-1" "collection-2"]
                                         (throw+
                                          (str "WRRCSS030: Solr cluster ID ["
                                               id "] does not exist."))))]
    (t/is (= (str "Available services in the 'dev' space:" new-line
                  "   [standard] retrieve_and_rank service named: rnr-1"
                  new-line
                  "      Cluster name: ke-1, size: 4, status: READY" new-line
                  "         configs: config-1, config-2" new-line
                  "         collections: collection-1, collection-2" new-line
                  "      Cluster name: ke-2, size: 2, status: NOT_AVAILABLE")
             (sut/list-services {:rnr-1 {:type "retrieve_and_rank"
                                         :plan "standard"
                                         :credentials {:url "http://rnr/api"
                                                       :username "user"
                                                       :password "pwd"}}}
                                "dev"
                                nil
                                nil)))))

(def magic-with-guid&credentials
  {:magic-1 {:type "cognition"
             :plan "standard"
             :guid "GUID-123456789"
             :credentials {:url "https://magic/api"
                           :username "user"
                           :password "secret"}}})

(t/deftest list-services-with-guid
  (with-redefs [cf/get-service-status (fn [_ _] "create succeeded")]
    (t/is (= (str "Available services in the 'dev' space:" new-line
                  "   [standard] cognition service named: magic-1" new-line
                  "      Service GUID: GUID-123456789" new-line)
             (sut/list-info {:org-space {:space "dev"}
                             :services magic-with-guid&credentials}
                            ["list" "services"]
                            ["--guid"])))))

(t/deftest list-services-with-credentials
  (with-redefs [cf/get-service-status (fn [_ _] "create succeeded")]
    (t/is (= (str "Available services in the 'dev' space:" new-line
                  "   [standard] cognition service named: magic-1" new-line
                  "      Service credentials:" new-line
                  "         {" new-line
                  "           \"url\" : \"https://magic/api\"," new-line
                  "           \"username\" : \"user\"," new-line
                  "           \"password\" : \"secret\"" new-line
                  "         }" new-line)
             (sut/list-info {:org-space {:space "dev"}
                             :services magic-with-guid&credentials}
                            ["list" "services"]
                            ["--credentials"])))))

(t/deftest list-services-with-guid&credentials
  (with-redefs [cf/get-service-status (fn [_ _] "create succeeded")]
    (t/is (= (str "Available services in the 'dev' space:" new-line
                  "   [standard] cognition service named: magic-1" new-line
                  "      Service GUID: GUID-123456789" new-line
                  "      Service credentials:" new-line
                  "         {" new-line
                  "           \"url\" : \"https://magic/api\"," new-line
                  "           \"username\" : \"user\"," new-line
                  "           \"password\" : \"secret\"" new-line
                  "         }" new-line)
             (sut/list-info {:org-space {:space "dev"}
                             :services magic-with-guid&credentials}
                            ["list" "services"]
                            ["--credentials" "--guid"])))))

(t/deftest list-services-without-credentials-try-flag
  (with-redefs [cf/get-service-status (fn [_ _] "create succeeded")]
    (t/is (= (str "Available services in the 'dev' space:" new-line
                  "   [standard] cognition service named: magic-1" new-line
                  "      WARNING: This service has no access credentials."
                  new-line)
             (sut/list-info {:org-space {:space "dev"}
                             :services {:magic-1 {:type "cognition"
                                                  :plan "standard"}}}
                            ["list" "services"]
                            ["--credentials"])))))

(t/deftest list-services-with-selections
  (with-redefs [cf/get-service-status (fn [_ _] "create succeeded")]
    (t/is (= (str "Available services in the 'dev' space:" new-line
                  "   [standard] database service named: simple" new-line
                  new-line
                  "Currently using the following selections:" new-line
                  "   document_conversion service:  dc" new-line
                  "   retrieve_and_rank service:    rnr" new-line
                  "   cluster:                      cluster" new-line
                  "   Solr configuration:           config" new-line
                  "   collection:                   collection" new-line)
             (sut/list-info
               {:org-space {:space "dev"}
                :services {:simple {:type "database"
                                    :plan "standard"
                                    :credentials {}}}
                :user-selections {:document_conversion "dc"
                                  :retrieve_and_rank "rnr"
                                  :cluster {:cluster_name "cluster"}
                                  :config {:config-name "config"}
                                  :collection
                                    {:collection-name "collection"}}}
                ["list" "services"]
                [])))))

(t/deftest list-selections-selections
  (t/is (= (str "Current environment:" new-line
                "   user:                         redshirt" new-line
                "   endpoint:                     https://api.endpoint.net"
                new-line
                "   org:                          org-name" new-line
                "   space:                        space-name" new-line
                new-line
                "Currently using the following selections:" new-line
                "   document_conversion service:  dc" new-line
                "   retrieve_and_rank service:    rnr" new-line
                "   cluster:                      cluster" new-line
                "   Solr configuration:           config" new-line
                "   collection:                   collection" new-line
                new-line
                "   conversion configuration:     convert.json" new-line
                "   document conversion version:  04-27-2016" new-line)
           (sut/list-info
             {:login {:username "redshirt"
                      :endpoint "https://api.endpoint.net"}
              :org-space {:org "org-name"
                          :space "space-name"}
              :services {}
              :user-selections {:document_conversion "dc"
                                :retrieve_and_rank "rnr"
                                :cluster {:cluster_name "cluster"}
                                :config {:config-name "config"}
                                :collection
                                  {:collection-name "collection"}
                                :conversion-configuration-file "convert.json"
                                :index-document-version "04-27-2016"}}
              ["list" "selections"]
              []))))

(t/deftest list-selections-no-selections
  (t/is (= (str "Current environment:" new-line
                "   user:                         redshirt" new-line
                "   endpoint:                     https://api.endpoint.net"
                new-line
                "   org:                          org-name" new-line
                "   space:                        space-name" new-line
                new-line)
           (sut/list-info
             {:login {:username "redshirt"
                      :endpoint "https://api.endpoint.net"}
              :org-space {:org "org-name"
                          :space "space-name"}
              :services {}}
              ["list" "selections"]
              []))))

(t/deftest list-everything-all
  (with-redefs [sut/list-service-selections (fn [_] "[service_selections]")
                sut/list-misc-selections (fn [_] "[misc_selections]")
                sut/list-working-environment (fn [_] "[environment]")
                sut/list-orgs (fn [_] "[organizations]")
                sut/list-spaces (fn [_ _ _] "[spaces]")
                sut/list-services (fn [_ _ _ _] "[services]")
                cf/get-service-status (fn [_ _] "create succeeded")]
    (t/is (= (str "[environment]" new-line
                  "[organizations]" new-line
                  "[spaces]" new-line
                  "[services]" new-line new-line
                  "Currently using the following selections:" new-line
                  "[service_selections]" new-line
                  "[misc_selections]")
             (sut/list-info {:login {:username "redshirt"
                                     :endpoint "https://api.endpoint.net"}
                             :org-space {:org "org-name"
                                         :space "space-name"}
                             :services {:simple {:type "database"}}}
                            ["list"]
                            [])))))

(t/deftest list-everything-no-services
  (with-redefs [sut/list-service-selections (fn [_] "[service_selections]")
                sut/list-misc-selections (fn [_] "[misc_selections]")
                sut/list-working-environment (fn [_] "[environment]")
                sut/list-orgs (fn [_] "[organizations]")
                sut/list-spaces (fn [_ _ _] "[spaces]")
                sut/list-services (fn [_ _ _ _] "[services]")
                cf/get-service-status (fn [_ _] "create succeeded")]
    (t/is (= (str "[environment]" new-line
                  "[organizations]" new-line
                  "[spaces]" new-line
                  "No services found in the 'space-name' space." new-line
                  "Use the 'create' command "
                  "to add services." new-line new-line
                  "Currently using the following selections:" new-line
                  "[service_selections]" new-line
                  "[misc_selections]")
             (sut/list-info {:login {:username "redshirt"
                                     :endpoint "https://api.endpoint.net"}
                             :org-space {:org "org-name"
                                         :space "space-name"}
                             :services {}}
                            ["list"]
                            [])))))

(t/deftest list-everything-no-selections
  (with-redefs [sut/list-service-selections (fn [_] nil)
                sut/list-misc-selections (fn [_] nil)
                sut/list-working-environment (fn [_] "[environment]")
                sut/list-orgs (fn [_] "[organizations]")
                sut/list-spaces (fn [_ _ _] "[spaces]")
                sut/list-services (fn [_ _ _ _] "[services]")
                cf/get-service-status (fn [_ _] "create succeeded")]
    (t/is (= (str "[environment]" new-line
                  "[organizations]" new-line
                  "[spaces]" new-line
                  "[services]" new-line)
             (sut/list-info {:login {:username "redshirt"
                                     :endpoint "https://api.endpoint.net"}
                             :org-space {:org "org-name"
                                         :space "space-name"}
                             :services {:simple {:type "database"}}}
                            ["list"]
                            [])))))
