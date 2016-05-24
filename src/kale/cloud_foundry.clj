;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.cloud-foundry
  (:require [kale.watson-service :as ws]
            [cheshire.core :as json]
            [kale.common :refer [fail trace-enabled set-trace new-line]]
            [clojure.string :refer [split]]
            [clojure.data.codec.base64 :as b64]
            [cheshire.core :as json]
            [slingshot.slingshot :refer [try+ throw+]]))

(defn cf-request
  "Make a HTTP request using the given function and handle
   any invalid token errors"
  [ws-func args]
  (try+
    (apply ws-func args)
    (catch
       (= 401 (:status %)) exception
       (try+
         (let [decoded (json/decode (exception :body))
               error_code (decoded "error_code")]
           (if (= error_code "CF-InvalidAuthToken")
             (fail (str "The authentication token for this session "
                        "is either invalid or expired." new-line
                        "Please run 'kale login' to acquire a new one."))
             (throw+ exception)))
         (catch Exception e (throw+ exception))))))

(defn cf-raw
  "HTTP request"
  [& args]
  (cf-request ws/raw args))

(defn cf-json
  "HTTP request, expecting the response body to be JSON"
  [& args]
  (cf-request ws/json args))

(defn cf-paged-json
  "HTTP request, expecting that the results might be across multiple pages"
  [& args]
  (let [{:keys [resources next_url]} (apply cf-json args)]
    (vec (concat resources
                 (when next_url
                   (let [new-args (assoc (vec args) 2 next_url)]
                      (apply cf-paged-json new-args)))))))

(defn get-oauth-tokens
  [username password url]
  (let [info (cf-json :get {:url url} "/v2/info")
        login-url (info :authorization_endpoint)
        tracing? @trace-enabled]
    ;; Don't trace an API call using user credentials!
    (set-trace false)
    (let [tokens (cf-json :post {:username "cf" :url login-url}
                          "/oauth/token"
                          {:form-params
                           {:password password
                            :username username
                            :grant_type "password"}})]
      (set-trace tracing?)
      tokens)))

(defn generate-auth
  "Generate map for authenticating Cloud Foundry API calls"
  [state]
    {:url (-> state :login :endpoint)
     :token (-> state :login :cf-token)})

(defn find-entity-by-element
  [resources element-name element]
  (first (filter #(= (-> % :entity element) element-name) resources)))

(defn find-key
  [resources guid]
  (find-entity-by-element resources guid :service_instance_guid))

(defn find-entity
  [resources entity-name]
  (find-entity-by-element resources entity-name :name))

(defn service-entry
  "Create a service entry with details about the service"
  [{:keys [guid name service_plan]} service-keys]
  (let [service-type (-> service_plan :service :label)
        service-key (find-key service-keys guid)
        plan-name (service_plan :name)
        key-guid (-> service-key :metadata :guid)
        credentials (-> service-key :entity :credentials)]
    {(keyword name) {:guid guid
                     :type service-type
                     :plan plan-name
                     :key-guid key-guid
                     :credentials credentials}}))

(defn get-services
  "Returns service information using the access token"
  [cf-auth space-guid]
  (let [summary-url (str "/v2/spaces/" space-guid "/summary")
        services ((cf-json :get cf-auth summary-url) :services)
        service-keys (cf-paged-json :get cf-auth "/v2/service_keys")
        map-function (fn [entity] (service-entry entity service-keys))]
    (into {} (map map-function services))))

(defn get-organizations
  "Pull information about available organizations"
  [cf-auth]
  (cf-paged-json :get cf-auth "/v2/organizations"))

(defn get-spaces
  "Pull information about available spaces for a given org"
  [cf-auth org-guid]
  (cf-paged-json :get cf-auth (str "/v2/organizations/" org-guid "/spaces")))

(defn get-user-guid
  "Extracts user-guid information from the access token"
  [token]
  (try+
    (let [encoded (second (split token #"\."))
          length (count encoded)
          padded (cond
                   (= (mod length 4) 2) (str encoded "==")
                   (= (mod length 4) 3) (str encoded "=")
                   :else encoded)
          decoded (String. (b64/decode (.getBytes padded)) "UTF-8")]
      ((json/decode decoded) "user_id"))
    (catch Exception e (fail "Unable to determine user ID."))))

(defn create-space
  "Creates a space on the specified organization"
  [cf-auth org-guid user-guid space-name]
  (cf-json :post cf-auth "/v2/spaces?async=true"
           {:body (json/encode
                    {:name space-name
                     :organization_guid org-guid
                     :developer_guids [user-guid]
                     :manager_guids [user-guid]})}))

(defn delete-space
  "Deletes a specified space"
  [cf-auth space-guid]
  ((cf-raw :delete cf-auth (str "/v2/spaces/" space-guid
                                "?recursive=true&async=true"))
     :body))

(defn get-service-plan-guid
  "Looks up service plan guid for a given service type and plan"
  [cf-auth space-guid service-type plan-name]
  (let [services-url (str "/v2/spaces/" space-guid
                          "/services?q=label%3A" service-type)
        services (cf-paged-json :get cf-auth services-url)
        service-guid (-> services first :metadata :guid)
        plans-url (str "/v2/service_plans?q=service_guid%3A" service-guid)
        plans (cf-paged-json :get cf-auth plans-url)]
    (-> (find-entity plans plan-name) :metadata :guid)))

(defn create-service
  "Creates an instance of a specified service plan"
  [cf-auth service-name space-guid plan-guid]
  (cf-json :post cf-auth "/v2/service_instances?accepts_incomplete=true"
           {:body (json/encode
                    {:name service-name
                     :space_guid space-guid
                     :service_plan_guid plan-guid})}))

(defn create-service-key
  "Creates a set of service keys for a specified service instance"
  [cf-auth service-name service-guid]
  (cf-json :post cf-auth "/v2/service_keys"
           {:body (json/encode
                    {:name (str service-name "_key")
                     :service_instance_guid service-guid})}))

(defn get-service-status
  "Check the status of the service"
  [cf-auth service-guid]
  (let [service-url (str "/v2/service_instances/" service-guid)
        service (cf-json :get cf-auth service-url)
        last-operation (-> service :entity :last_operation)]
    (str (last-operation :type) " " (last-operation :state))))

(defn delete-service-key
  "Delete a specific service key from a service instance"
  [cf-auth key-guid]
  ((cf-raw :delete cf-auth (str "/v2/service_keys/" key-guid "?async=true"))
    :body))

(defn delete-service
  "Delete a specific service instance"
  [cf-auth service-guid]
  ((cf-raw :delete cf-auth (str "/v2/service_instances/" service-guid
                                 "?accepts_incomplete=true&async=true"))
    :body))
