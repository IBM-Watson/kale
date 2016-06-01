;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.retrieve-and-rank-test
  (:require [kale.retrieve-and-rank :as rnr]
            [kale.common :refer [fail new-line set-language]]
            [clj-http.fake :refer [with-fake-routes-in-isolation]]
            [slingshot.test :refer :all]
            [slingshot.slingshot :refer [try+]]
            [cheshire.core :as json]
            [clojure.test :refer :all]
            [clojure.java.io :as io]))

(set-language :en)

(deftest validate-solr-name-bad-chars
  (map (fn [solr-name]
         (is (thrown+-with-msg?
              [:type :kale.common/fail]
              #"Invalid object name"
              (rnr/validate-solr-name solr-name))))
        ["my-obj#",
         "my@obj",
         "my%obj()",
         "my obj"]))

(deftest validate-solr-name-ok
  (is (= nil
         (rnr/validate-solr-name "my-obj.TO_TEST"))))

(deftest rnr-request-no-credentials
  (is (thrown+-with-msg?
         [:type :kale.common/fail]
         #"Target retrieve_and_rank service has no access credentials."
         (rnr/list-clusters nil))))

(def template-response
  {:status 200
   :headers {"Content-Type" "application/json"}
   :request-time 100
   :body ""
   :cookies {"Watson-DPAT" {:discard true
                            :path "/retrieve-and-rank/api"
                            :secure true
                            :value "LONG"
                            :version 0}}})

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

(def endpoint
  {:url "https://gateway.watsonplatform.net/retrieve-and-rank/api",
   :username "user"
   :password "pwd"})

(defn rnr-url
  [tail]
  (-> endpoint :url (str tail)))

(defn respond
  [partial-response]
  (fn [request] (merge template-response partial-response)))

(deftest list-no-clusters
  (with-fake-routes-in-isolation
    {(rnr-url "/v1/solr_clusters")
     (respond {:body (json/encode {:clusters []})})}
    (is (= [] (rnr/list-clusters endpoint)))))

(deftest list-one-cluster
  (with-fake-routes-in-isolation
    {(rnr-url "/v1/solr_clusters")
     (respond {:body (json/encode {:clusters [cluster-1]})})}
    (is (= [cluster-1] (rnr/list-clusters endpoint)))))

(deftest list-two-clusters
  (with-fake-routes-in-isolation
    {(rnr-url "/v1/solr_clusters")
     (respond {:body (json/encode {:clusters [cluster-1 cluster-2]})})}
    (is (= [cluster-1 cluster-2] (rnr/list-clusters endpoint)))))

(deftest get-cluster
  (with-fake-routes-in-isolation
    {(rnr-url "/v1/solr_clusters/CLUSTER_ID")
     (respond {:body (json/encode cluster-1)})}
    (is (= cluster-1 (rnr/get-cluster endpoint "CLUSTER_ID")))))

(deftest list-no-configs
  (with-fake-routes-in-isolation
    {(rnr-url "/v1/solr_clusters/CLUSTER_ID/config")
     (respond {:body (json/encode {:solr_configs []})})}
    (is (= [] (rnr/list-configs endpoint "CLUSTER_ID")))))

(deftest list-two-configs
  (with-fake-routes-in-isolation
    {(rnr-url "/v1/solr_clusters/CLUSTER_ID/config")
     (respond {:body (json/encode {:solr_configs ["abcd" "wxyz"]})})}
    (is (= ["abcd" "wxyz"] (rnr/list-configs endpoint "CLUSTER_ID")))))

(deftest list-no-collections
  (with-fake-routes-in-isolation
    {(rnr-url (str  "/v1/solr_clusters/CLUSTER_ID/solr"
                    "/admin/collections?action=LIST&wt=json"))
     (respond {:body (json/encode {:collections []})})}
    (is (= [] (rnr/list-collections endpoint "CLUSTER_ID")))))

(deftest list-two-collections
  (with-fake-routes-in-isolation
    {(rnr-url (str "/v1/solr_clusters/CLUSTER_ID/solr"
                   "/admin/collections?action=LIST&wt=json"))
     (respond {:body (json/encode {:collections ["fred" "mary"]})})}
    (is (= ["fred" "mary"] (rnr/list-collections endpoint "CLUSTER_ID")))))

(defn create-cluster-response
  [size]
  {:solr_cluster_id "CLUSTER_ID"
   :cluster_name "cluster-2"
   :cluster_size size
   :solr_cluster_status "NOT_AVAILABLE"})

(deftest create-cluster-default-size
  (with-fake-routes-in-isolation
    {(rnr-url "/v1/solr_clusters")
     (respond {:body (json/encode (create-cluster-response ""))})}
    (is (= (create-cluster-response "")
           (rnr/create-cluster endpoint "cluster-2" nil)))))

(deftest create-cluster-given-size
  (with-fake-routes-in-isolation
    {(rnr-url "/v1/solr_clusters")
     (respond {:body (json/encode (create-cluster-response "2"))})}
    (is (= (create-cluster-response "2")
           (rnr/create-cluster endpoint "cluster-2" 2)))))

(def create-cluster-no-free
  (str "WRRCSR042: The requesting service instance may not "
       "create any more free Solr clusters (current limit: 1)."))

(deftest create-cluster-no-free-catch
  (with-fake-routes-in-isolation
    {(rnr-url "/v1/solr_clusters")
     (respond {:status 409 :body create-cluster-no-free})}
    (try+
      (rnr/create-cluster endpoint "cluster-2" nil)
      (fail "Should have thrown exception.")
      (catch
        (= (:status %) 409)
        {:keys [body]}
        (is (re-matches
              (re-pattern (str ".*" new-line
                               "Try specifying a cluster size instead of "
                               "using the default 'free' size."))
              body))))))

(def delete-cluster-missing
  {:msg "WRRCSS030: Solr cluster ID [CLUSTER_ID] does not exist."
   :code 404})

(deftest delete-cluster-nonexistent
  (with-fake-routes-in-isolation
    {(rnr-url "/v1/solr_clusters/CLUSTER_ID")
     (respond {:status 404 :body (json/encode delete-cluster-missing)})}
    (is (thrown+-with-msg?
         [:status 404]
         #"clj-http: status 404"
         (rnr/delete-cluster endpoint "CLUSTER_ID")))))

(def delete-cluster-response
  {:message "WRRCSR023: Successfully deleted Solr cluster [CLUSTER_ID]."
   :statusCode 200})

(deftest delete-cluster-good
  (with-fake-routes-in-isolation
    {(rnr-url "/v1/solr_clusters/CLUSTER_ID")
     (respond {:body (json/encode delete-cluster-response)})}
    (is (= delete-cluster-response
           (rnr/delete-cluster endpoint "CLUSTER_ID")))))

(def config-already-exists
  {:msg (str "WRRCSS002: Solr config [duplicate-config] already exists."
             " Delete configuration before uploading changes.")
   :code 409})

(deftest upload-config-duplicate
  (with-fake-routes-in-isolation
    {(rnr-url "/v1/solr_clusters/CLUSTER_ID/config/duplicate-config")
     (respond {:status 409 :body (json/encode config-already-exists)})}
    (is (thrown+-with-msg?
         [:status 409]
         #"clj-http: status 409"
         (rnr/upload-config endpoint "CLUSTER_ID" "duplicate-config"
                            "file.zip")))))

(defn config-upload-success
  [config-name cluster-id]
  {:message (str "WRRCSR026: Successfully uploaded named config ["
                 config-name
                 "] for Solr cluster [" cluster-id "].")
   :statusCode 200})

(deftest upload-config-success
  (with-fake-routes-in-isolation
    {(rnr-url "/v1/solr_clusters/CLUSTER_ID/config/good-config")
     (respond {:status 200
               :body (json/encode (config-upload-success
                                   "good-config" "CLUSTER_ID"))})}
    (is (= (config-upload-success "good-config" "CLUSTER_ID")
           (rnr/upload-config endpoint "CLUSTER_ID" "good-config"
                              "file.zip")))))

(defn config-delete-in-use
  [config-name collection-name]
  {:msg (str "WRRCSS006: Configuration ["
             config-name
             "] is currently in use by collection ["
             collection-name
             "] in Solr. Delete that collection before proceeding.")
   :code 400})

(deftest delete-config-in-use
  (with-fake-routes-in-isolation
    {(rnr-url "/v1/solr_clusters/CLUSTER_ID/config/in-use-config")
     (respond {:status 400 :body (json/encode (config-delete-in-use
                                               "in-use-config"
                                               "live-collection"))})}
    (is (thrown+-with-msg?
         [:status 400]
         #"clj-http: status 400"
         (rnr/delete-config endpoint "CLUSTER_ID" "in-use-config")))))

(defn config-delete-success
  [config-name cluster-id]
  {:message (str "WRRCSR025: Successfully deleted named config ["
                 config-name
                 "] for Solr cluster [" cluster-id "].")
   :statusCode 200})

(deftest delete-config-success
  (with-fake-routes-in-isolation
    {(rnr-url "/v1/solr_clusters/CLUSTER_ID/config/some-config")
     (respond {:status 200
               :body (json/encode (config-delete-success
                                   "some-config" "CLUSTER_ID"))})}
    (is (= (config-delete-success "some-config" "CLUSTER_ID")
           (rnr/delete-config endpoint "CLUSTER_ID" "some-config")))))

(defn exception-response
  [message]
  {"responseHeader" {
     "status" 400
     "QTime" 100}
   "Operation create caused exception:" "org.apache.solr.common.SolrException"
   "exception" {
     "msg" message
     "rspCode" 400}})

(defn solr-error-response
  [message]
  {"solrErrorMessage" {
    "message" message}})

(defn existing-collection
  [collection-name]
  (exception-response (str "collection already exists: " collection-name)))

(defn bad-collection-name
  [collection-name]
  (solr-error-response (str "WRRCSR046: Invalid [name] identifier provided: ["
                            collection-name "].")))

(defn missing-collection
  [collection-name]
  (exception-response (str "Could not find collection : " collection-name)))

(def collection-success
  {:responseHeader {:status 0
                    :QTime 100}
   :success {:responseHeader {:status 0
                              :QTime 100}}})

(deftest create-collection-existing-name
  (with-fake-routes-in-isolation
    {(rnr-url (str "/v1/solr_clusters/CLUSTER_ID/solr/admin/collections?"
                   "action=CREATE&name=new-collection"
                   "&collection.configName=config&wt=json"))
     (respond {:status 400
               :body (json/encode (existing-collection "new-collection"))})}
    (is (thrown+-with-msg?
         [:type :kale.common/fail]
         #"collection already exists: new-collection"
         (rnr/create-collection endpoint "CLUSTER_ID"
                                "config" "new-collection")))))

(deftest create-collection-bad-name
  (with-fake-routes-in-isolation
    {(rnr-url (str "/v1/solr_clusters/CLUSTER_ID/solr/admin/collections?"
                   "action=CREATE&name=new-collection"
                   "&collection.configName=config&wt=json"))
     (respond {:status 400
               :body (json/encode (bad-collection-name "new-collection"))})}
    (is (thrown+-with-msg?
         [:type :kale.common/fail]
         #"WRRCSR046: Invalid \[name\] identifier provided: \[new-collection\]."
         (rnr/create-collection endpoint "CLUSTER_ID"
                                "config" "new-collection")))))

(deftest create-collection-success
  (with-fake-routes-in-isolation
    {(rnr-url (str "/v1/solr_clusters/CLUSTER_ID/solr/admin/collections?"
                   "action=CREATE&name=new-collection"
                   "&collection.configName=config&wt=json"))
     (respond {:body (json/encode collection-success)})}
    (is (= collection-success
           (rnr/create-collection endpoint "CLUSTER_ID"
                                  "config" "new-collection")))))

(deftest delete-collection-bad-name
  (with-fake-routes-in-isolation
    {(rnr-url (str "/v1/solr_clusters/CLUSTER_ID/solr/admin/collections?"
                   "action=DELETE&name=doomed-collection&wt=json"))
     (respond {:status 400
               :body (json/encode (missing-collection "doomed-collection"))})}
    (is (thrown+-with-msg?
         [:type :kale.common/fail]
         #"Could not find collection : doomed-collection"
         (rnr/delete-collection endpoint "CLUSTER_ID" "doomed-collection")))))

(deftest delete-collection-success
  (with-fake-routes-in-isolation
    {(rnr-url (str "/v1/solr_clusters/CLUSTER_ID/solr/admin/collections?"
                   "action=DELETE&name=doomed-collection&wt=json"))
     (respond {:body (json/encode collection-success)})}
    (is (= collection-success
           (rnr/delete-collection endpoint "CLUSTER_ID" "doomed-collection")))))

(defn query-response
  [docs]
  {:responseHeader {:status 0
                    :QTime 100
                    :params {:q "sasquadch"
                             :wt "json"}}
   :response {:numFound (count docs)
              :start 0
              :docs docs}})

(deftest query-no-results
  (with-fake-routes-in-isolation
    {(rnr-url (str "/v1/solr_clusters/CLUSTER_ID/solr"
                   "/COLLECTION/select?q=sasquadch"
                   "&wt=json"
                   "&fl=id%2Ctitle"
                   "&hl=true"
                   "&hl.fl=body"
                   "&hl.fragsize=100"))
     (respond {:body (json/encode (query-response []))})}
    (is (= (query-response [])
           (rnr/query endpoint "CLUSTER_ID" "COLLECTION" "sasquadch")))))
