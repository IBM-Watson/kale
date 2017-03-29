;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.retrieve-and-rank
  (:require [kale.watson-service :as ws]
            [kale.common :refer [fail new-line get-command-msg]]
            [clojure.string :as str]
            [cheshire.core :as json]
            [slingshot.slingshot :refer [try+ throw+]]))

(defn get-msg
  "Return the corresponding service message"
   [msg-key & args]
   (apply get-command-msg :service-messages msg-key args))

(defn exception-response-msg
  "Parse exception response message sent from R&R service"
  [response]
  (if-let [exception (response :exception)]
    (exception :msg)))

(defn solr-error-msg
  "Parse Solr error message sent from R&R service"
  [response]
  (if-let [exception (response :solrErrorMessage)]
    (exception :message)))

(defn process-json-exception
  "Try to interpret the exception json returned by R&R"
  [exception]
  (try+
    (let [decoded (json/decode exception true)]
      (if-let [message (or (exception-response-msg decoded)
                           (solr-error-msg decoded))]
        (fail message)
        (fail decoded)))
    (catch Exception e (fail exception))))

(defn validate-solr-name
  "Check if the solr object name contains valid characters"
  [solr-name]
  (when-not (re-matches #"^[-._a-zA-Z0-9]*$" solr-name)
    (fail (get-msg :invalid-solr-name solr-name))))

(defn rnr-request
  "Make a HTTP request using the given function, but first check
   for missing credentials."
  [ws-func args]
  (if (nil? (second args))
    (fail (get-msg :rnr-no-creds))
    (apply ws-func args)))

(defn rnr-text
  "HTTP request, expecting the response body to be plaintext"
  [& args]
  (rnr-request ws/text args))

(defn rnr-json
  "HTTP request, expecting the response body to be JSON"
  [& args]
  (rnr-request ws/json args))

(defn list-clusters
  "List of Solr clusters."
  [endpoint]
  (:clusters (rnr-json :get endpoint "/v1/solr_clusters")))

(defn get-cluster
  "Get a specific Solr cluster."
  [endpoint cluster-id]
  (rnr-json :get endpoint (str "/v1/solr_clusters/" cluster-id)))

(defn list-configs
  "List of Solr configurations"
  [endpoint cluster-id]
  (:solr_configs (rnr-json :get endpoint (str "/v1/solr_clusters/"
                                              cluster-id
                                              "/config"))))

(defn list-collections
  "List of Solr collections"
  [endpoint cluster-id]
  (:collections (rnr-json
                  :get endpoint
                  (str "/v1/solr_clusters/"
                       cluster-id
                       "/solr/admin/collections?action=LIST&wt=json"))))

(defn create-cluster
  "Create a Solr cluster."
  [endpoint cluster-name cluster-size]
  (try+
    (let [args (if cluster-size {:cluster_name cluster-name
                                 :cluster_size cluster-size}
                   {:cluster_name cluster-name})]
      (rnr-json :post endpoint
                (str "/v1/solr_clusters")
                {:content-type :json
                 :body (json/encode args)}))
    (catch (let [{:keys [status body]} %]
             (and (= 409 status)
                  (re-matches #"WRRCSR042.*" body)))
           exception
           (throw+ (update-in
                      exception [:body] str
                      new-line (get-msg :cluster-hint))))))

(defn delete-cluster
  "Delete a Solr cluster."
  [endpoint cluster-id]
  (rnr-json :delete endpoint
            (str "/v1/solr_clusters/" cluster-id)))

(defn query
  "Query a collection."
  [endpoint cluster-id collection-name query-string]
  (rnr-json :get endpoint
            (str "/v1/solr_clusters/" cluster-id
                 "/solr/" collection-name "/select")
            {:query-params {:q query-string
                            :wt "json"
                            :fl "id,title"
                            :hl true
                            :hl.fl "body"
                            :hl.fragsize 100}}))

(defn upload-config
  "Upload a new Solr configuration."
  [endpoint cluster-id config-name zip-file]
  (rnr-json :post endpoint
            (str "/v1/solr_clusters/" cluster-id
                 "/config/" config-name)
            {:content-type :zip
             :body zip-file}))

(defn download-config
  "Download an existing Solr configuration.
  The configuration is packaged as a zip file.
  The contents of the zip file are returned as a byte array."
  [endpoint cluster-id config-name]
  (rnr-text :get endpoint
            (str "/v1/solr_clusters/" cluster-id
                 "/config/" config-name)
            {:accept :zip
             :as :byte-array}))

(defn delete-config
  "Delete an existing Solr configuration."
  [endpoint cluster-id config-name]
  (rnr-json :delete endpoint
            (str "/v1/solr_clusters/" cluster-id
                 "/config/" config-name)))

(defn create-collection
  "Create a Solr collection"
  [endpoint cluster-id config-name collection-name]
  (try+
    (rnr-json :post endpoint
              (str "/v1/solr_clusters/" cluster-id
                   "/solr/admin/collections")
              {:query-params {:action "CREATE"
                              :name collection-name
                              :collection.configName config-name
                              :wt "json"}})
    (catch (and (number? (:status %)) (< 200 (:status %)))
           {:keys [body]}
           (process-json-exception body))))

(defn delete-collection
  "Delete a Solr collection"
  [endpoint cluster-id collection-name]
  (try+
    (rnr-json :post endpoint
              (str "/v1/solr_clusters/" cluster-id
                   "/solr/admin/collections")
              {:query-params {:action "DELETE"
                              :name collection-name
                              :wt "json"}})
    (catch (and (number? (:status %)) (< 200 (:status %)))
           {:keys [body]}
           (process-json-exception body))))
