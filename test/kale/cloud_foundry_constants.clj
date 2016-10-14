;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.cloud-foundry-constants)

(def template-response
  {:status 200
   :headers {"Content-Type" "application/json"}
   :request-time 100
   :body ""})

(defn service-entity
  [guid service-name service-type]
  {:guid guid
   :name service-name
   :last_operation {
     :type "create"
     :state "succeeded"
     :description ""
     :updated_at nil
     :created_at "DATE"}
   :service_plan {
     :guid (str "PLAN_" guid)
     :name "standard"
     :service {
       :guid (str "TYPE_" guid)
       :label service-type
       :provider nil
       :version nil}}})

(def space-summary-response
  {:guid "SPACE_GUID"
   :name "dev"
   :apps []
   :services [
     (service-entity "RNR_GUID" "rnr-service" "retrieve_and_rank")
     (service-entity "DC_GUID" "dc-service" "document_conversion")]})

(defn service-instance-entity
  [guid service-name]
  {:metadata {
    :guid guid
    :url (str "/v2/service_instances/" guid)
    :created_at "DATE"
    :updated_at nil}
  :entity {
    :name service-name
    :credentials {}
    :service_plan_guid (str "PLAN_" guid)
    :space_guid (str "SPACE_" guid)
    :gateway_data nil
    :dashboard_url "https://www.ibm.com/service-dashboard.html"
    :type "managed_service_instance"
    :last_operation {
      :type "create"
      :state "succeeded"
      :description ""
      :updated_at nil
      :created_at "DATE"}
    :tags []
    :space_url (str "/v2/spaces/SPACE_" guid)
    :service_plan_url (str "/v2/service_plans/PLAN_" guid)
    :service_bindings_url (str "/v2/service_instances/"
                               guid "/service_bindings")
    :service_keys_url (str "/v2/service_instances/" guid "/service_keys")
    :routes_url (str "/v2/service_instances/" guid "/routes")}})

(defn service-key-entity
  [guid service-type]
  {:metadata {
     :guid (str "KEY_" guid)
     :url (str "/v2/service_keys/KEY_" guid)
     :created_at "DATE"
     :updated_at nil}
   :entity {
     :name "credentials"
     :service_instance_guid guid
     :credentials {
       :url (str "https://gateway.watsonplatform.net/" service-type "/api")
       :username "redshirt"
       :password "scotty"}
     :service_instance_url (str "/v2/service_instances/" guid)}})

(defn org-entity
  [guid name]
  {:metadata {
     :guid guid
     :url (str "/v2/organizations/" guid)
     :created_at "DATE"
     :updated_at nil}
   :entity {
     :managers_url (str "/v2/organizations/" guid "/managers")
     :private_domains_url (str "/v2/organizations/" guid "/private_domains")
     :name name
     :billing_enabled false
     :spaces_url (str "/v2/organizations/" guid "/spaces")
     :status "active"
     :domains_url (str "/v2/organizations/" guid "/domains")
     :users_url (str "/v2/organizations/" guid "/users")}})

(defn space-entity
  [guid name]
  {:metadata {
     :guid guid
     :url (str "/v2/spaces/" guid)
     :created_at "DATE"
     :updated_at nil}
   :entity {
     :apps_url (str "/v2/spaces/" guid)
     :allow_ssh true
     :developers_url (str "/v2/spaces/" guid "/developers")
     :name name
     :organization_url "/v2/organizations/ORG_GUID"
     :service_instances_url (str "/v2/spaces/" guid "/service_instances")
     :routes_url (str "/v2/spaces/" guid "/routes")
     :domains_url (str "/v2/spaces/" guid "/domains")
     :organization_guid "ORG_GUID"}})

(defn service-type-entity
  [guid name]
  {:metadata {
     :guid guid
     :url (str "/v2/services/" guid)
     :created_at "DATE"
     :updated_at "DATE"}
   :entity {
     :label name
     :description "This is a service description."
     :long_description nil
     :version nil
     :active true
     :bindable true
     :unique_id (str "UNIQUE_" guid)
     :extra ""
     :documentation_url nil
     :service_broker_guid (str "BROKER_" guid)
     :plan_updateable false
     :service_plans_url (str "/v2/services/" guid "/service_plans")}})

(defn service-plan-entity
  [guid name]
  {:metadata {
     :guid guid
     :url (str "/v2/service_plans/" guid)
     :created_at "DATE"
     :updated_at "DATE"}
   :entity {
     :name name
     :free false
     :description "Details on how services are charged."
     :service_guid (str "TYPE_" guid)
     :extra ""
     :unique_id (str "UNIQUE_" guid)
     :public true
     :active true
     :service_url (str "/v2/services/TYPE_" guid)
     :service_instances_url (str "/v2/service_plans/"
                                 guid "/service_instances")}})

(defn results-response
  [resources]
  {:total_results (count resources)
   :total_pages 1
   :prev_url nil
   :next_url nil
   :resources resources})

(def service-keys-response
   (results-response [
     (service-key-entity "RNR_GUID" "retrieve_and_rank")
     (service-key-entity "DC_GUID" "document_conversion")]))

(def orgs-response
   (results-response [
     (org-entity "ORG_GUID1" "org1")
     (org-entity "ORG_GUID2" "org2")]))

(def spaces-response
   (results-response [
     (space-entity "SPACE_GUID1" "space1")
     (space-entity "SPACE_GUID2" "space2")]))

(def service-type-response
   (results-response [
     (service-type-entity "TYPE_GUID" "service-type")]))

(def service-plan-response
   (results-response [
     (service-plan-entity "PLAN_GUID" "standard")]))

(defn respond
  [partial-response]
  (merge template-response partial-response))

(def cf-auth
  {:url "https://api.ng.bluemix.net"
   :token "TOKEN"})

(defn cf-url
  [tail]
  (-> cf-auth :url (str tail)))

(defn service-entry
  [guid service-name service-type]
  {(keyword service-name) {
    :credentials {
      :password "scotty"
      :url (str "https://gateway.watsonplatform.net/" service-type "/api")
      :username "redshirt" }
    :guid guid,
    :type service-type
    :plan "standard"
    :key-guid (str "KEY_" guid)}})

(def entry1 (service-entry "RNR_GUID" "rnr-service" "retrieve_and_rank"))
(def entry2 (service-entry "DC_GUID" "dc-service" "document_conversion"))

(def org-space-entry
  {:org "org-name"
   :space "space-name"
   :guid {
     :org "ORG_GUID"
     :space "SPACE_GUID"}})
