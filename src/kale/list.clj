;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.list
  (:require [kale.common :refer [fail new-line get-options
                                 reject-extra-args get-command-msg]]
            [kale.cloud-foundry :as cf]
            [kale.retrieve-and-rank :as rnr]
            [kale.aliases :as aliases]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.set :as set]))

(defn get-msg
  "Return the corresponding list message"
   [msg-key & args]
   (apply get-command-msg :list-messages msg-key args))

(def select-items
  {:organizations aliases/organizations
   :spaces aliases/spaces
   :services aliases/services
   :selections aliases/selections})

(defmulti list-info (fn [_ [_ what & _] _]
                      (or (some (fn [[k a]] (when (a what) k)) select-items)
                          :unknown)))

(defn list-orgs
  "List the available organizations to the user"
  [cf-auth]
  (let [orgs (cf/get-organizations cf-auth)
        org-names (map (fn [s] (-> s :entity :name)) orgs)]
    (str (get-msg :available-orgs) new-line "   "
         (str/join (str new-line "   ") org-names)
         new-line)))

(defmethod list-info :organizations
  [state [cmd what & args] flags]
  (reject-extra-args args cmd what)
  (get-options flags {})
  (let [cf-auth (cf/generate-auth state)
        current-org (-> state :org-space :org)]
    (str (list-orgs cf-auth) new-line
         (get-msg :current-org current-org))))

(defn list-spaces
  "List the available spaces in the user's current space"
  [cf-auth org-name org-guid]
  (let [spaces (cf/get-spaces cf-auth org-guid)
        space-names (map (fn [s] (-> s :entity :name)) spaces)]
    (str (get-msg :available-spaces org-name) new-line "   "
         (str/join (str new-line "   ") space-names)
         new-line)))

(defmethod list-info :spaces
  [state [cmd what & args] flags]
  (reject-extra-args args cmd what)
  (get-options flags {})
  (let [cf-auth (cf/generate-auth state)
        current-space (-> state :org-space :space)
        org-name (-> state :org-space :org)
        org-guid (-> state :org-space :guid :org)]
    (str (list-spaces cf-auth org-name org-guid) new-line
         (get-msg :current-space current-space))))

(defn rnr-cluster-detail
  [credentials
   {:keys [solr_cluster_id cluster_name cluster_size solr_cluster_status]}]
  (str new-line (get-msg :cluster-info cluster_name
                         (if (empty? cluster_size)
                           (get-msg :cluster-free-size)
                           cluster_size)
                         solr_cluster_status)
       (when (= "READY" solr_cluster_status)
         (str new-line (get-msg :cluster-configs)
              (str/join ", " (rnr/list-configs credentials
                                               solr_cluster_id))
              new-line (get-msg :cluster-collections)
              (str/join ", " (rnr/list-collections credentials
                                                   solr_cluster_id))))))

(defmulti service-detail :type)

(defmethod service-detail :default [_] nil)

(defmethod service-detail "retrieve_and_rank" rnr-detail
  [{:keys [credentials]}]
  (str/join (map #(rnr-cluster-detail credentials %)
                 (rnr/list-clusters credentials))))

(defn indent
  [spaces body]
  (str spaces (str/replace body #"\r?\n" (str new-line spaces))))

(defn list-services
  "List each known service, including its name and type."
  [services space-name guids? creds?]
  (str (get-msg :available-services space-name) new-line
       (str/join
        (str new-line new-line)
        (map (fn [[key {:keys [type plan guid credentials] :as service}]]
               (str (get-msg :service-info
                             (if plan
                               (str "[" plan "] ")
                               "")
                             (or type "user-provided")
                             (name key))
                    (when guids? (str new-line (get-msg :service-guid guid)))
                    (when (and creds? (some? credentials))
                      (str new-line (get-msg :service-cred) new-line
                           (indent "         "
                                   (json/encode credentials
                                                {:pretty true}))))
                    (if (some? credentials)
                      (service-detail service)
                      (str new-line (get-msg :service-no-cred)))))
             services))))

(defn list-selection
  "Generate a string giving a selection's value"
  [label value]
  (when value
    (str "   " (format "%-30s" (str label ":")) value new-line)))

(defn list-working-environment
  "Get information related to the user's current environment"
  [{:keys [login org-space]}]
  (str (get-msg :current-environment) new-line
       (list-selection (get-msg :user-select) (login :username))
       (list-selection (get-msg :endpoint-select) (login :endpoint))
       (list-selection (get-msg :org-select) (org-space :org))
       (list-selection (get-msg :space-select) (org-space :space))))

(defn list-service-selections
  "Get user-selections related to services"
  [state]
  (when (state :user-selections)
    (let [{:keys [document_conversion
                  retrieve_and_rank
                  cluster
                  collection
                  config]} (state :user-selections)]
      (str (list-selection (get-msg :dc-select) document_conversion)
           (list-selection (get-msg :rnr-select) retrieve_and_rank)
           (when cluster
             (list-selection (get-msg :cluster-select)
                             (cluster :cluster_name)))
           (when config
             (list-selection (get-msg :config-select)
                             (config :config-name)))
           (when collection
             (list-selection (get-msg :collection-select)
                             (collection :collection-name)))))))

(defn list-misc-selections
  "Get user-selections that aren't related to services."
  [state]
  (when (state :user-selections)
    (let [{:keys [user-selections]} state
          convert-config (user-selections :conversion-configuration-file)
          index-version (user-selections :index-document-version)]
      (str (list-selection (get-msg :convert-select) convert-config)
           (list-selection (get-msg :dc-version-select) index-version)))))

(def list-services-options
  {:guid aliases/guid-option
   :cred aliases/credentials-option})

(defn get-available-services
  "Return only the services that aren't in a bad state."
  [cf-auth services]
  (filter (fn [service]
            (let [[_ {:keys [guid]}] service
                  status (cf/get-service-status cf-auth guid)]
              (when-not (or (re-find #"delete" status)
                            (= "create failed" status))
                service)))
          services))

(defmethod list-info :services
  [state [cmd what & args] flags]
  (reject-extra-args args cmd)
  (let [options (get-options flags list-services-options)
        cf-auth (cf/generate-auth state)
        {:keys [services org-space]} state
        available-services (get-available-services cf-auth services)
        space-name (org-space :space)
        selections (list-service-selections state)]
    (when (empty? available-services)
      (fail (get-msg :no-services space-name)))
    (str (list-services available-services
                        space-name
                        (options :guid)
                        (options :cred))
         new-line
         (when (seq selections)
           (str new-line (get-msg :current-selections) new-line
                selections)))))

(defmethod list-info :selections
  [state [cmd what & args] flags]
  (reject-extra-args args cmd)
  (get-options flags {})
  (let [services-select (list-service-selections state)
        misc-select (list-misc-selections state)]
    (str (list-working-environment state) new-line
         (when (or (seq services-select) (seq misc-select))
           (str (get-msg :current-selections) new-line
                (list-service-selections state) new-line
                (list-misc-selections state))))))

(defn list-everything
  "List everything that can be listed"
  [state args flags]
  (let [options (get-options flags list-services-options)
        cf-auth (cf/generate-auth state)
        {:keys [services org-space]} state
        available-services (get-available-services cf-auth services)
        org-name (org-space :org)
        org-guid (-> org-space :guid :org)
        space-name (org-space :space)

        services-select (list-service-selections state)
        misc-select (list-misc-selections state)]
    (str (list-working-environment state) new-line
         (list-orgs cf-auth) new-line
         (list-spaces cf-auth org-name org-guid) new-line
         (if (empty? available-services)
           (get-msg :no-services space-name)
           (list-services available-services
                          space-name
                          (options :guid)
                          (options :cred)))
         new-line
         (when (or (seq services-select) (seq misc-select))
           (str new-line (get-msg :current-selections) new-line
                (list-service-selections state) new-line
                (list-misc-selections state))))))

(defmethod list-info :unknown
  [state [cmd what & args] flags]
  (if (empty? what)
    (list-everything state args flags)
    (fail (get-msg :unknown-list cmd what cmd
                   (str/join (str new-line "   ")
                             ["spaces" "organizations"
                              "services" "selections"]) cmd))))
