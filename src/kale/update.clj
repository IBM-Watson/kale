;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.update
  (:require [kale.cloud-foundry :as cf]
            [kale.retrieve-and-rank :as rnr]
            [kale.common :refer [get-command-msg]]
            [kale.persistence :as persist]
            [slingshot.slingshot :refer [try+]]))

(def element-hierarchy
  {:services [:retrieve_and_rank :document_conversion]
   :retrieve_and_rank [:cluster]
   :cluster [:config :collection]})

(defn get-child-elements
  [item-key]
  (let [elements (element-hierarchy item-key)]
    (concat elements (mapcat get-child-elements elements))))

(defn missing-cluster?
  "Check if the specified service contains the cluster selection."
  [selection endpoint]
  (let [cluster-id (:solr_cluster_id selection)
        clusters (rnr/list-clusters endpoint)]
    (empty? (filter #(= cluster-id (:solr_cluster_id %)) clusters))))

(defn missing-config?
  "Check if the specified service contains the config selection."
  [selection endpoint]
  (let [config-name (:config-name selection)
        configs (rnr/list-configs endpoint (:cluster-id selection))]
    (empty? (filter #(= config-name %) configs))))

(defn missing-collection?
  "Check if the specified service contains the collection selection."
  [selection endpoint]
  (let [collection-name (:collection-name selection)
        collections (rnr/list-collections endpoint (:cluster-id selection))]
    (empty? (filter #(= collection-name %) collections))))

(defn missing-selection?
  "Check if the specified selection exists."
  [item-key {:keys [user-selections services]}]
  (if-let [selection (item-key user-selections)]
    (if (or (= item-key :retrieve_and_rank)
            (= item-key :document_conversion))
      (nil? ((keyword selection) services))
      (let [kw (keyword (:service-key selection))]
        ;; If the parent service exists
        (if-let [endpoint (-> services kw :credentials)]
          (try+ (cond
                 (= item-key :cluster)
                 (missing-cluster? selection endpoint)
                 (= item-key :config)
                 (missing-config? selection endpoint)
                 (= item-key :collection)
                 (missing-collection? selection endpoint))
            (catch (number? (:status %)) _ true))
        ;; If the service is lacking credentials, assume the element is
        ;; missing (we couldn't access it anyways if it existed)
         true)))
    ;; The selection doesn't exist
    false))

(defn list-missing-selections
  "Check if any of the selections are missing (starting at 'item-key')
   and return a list of them."
  [item-key state]
  (if (missing-selection? item-key state)
    (concat [item-key] (get-child-elements item-key))
    (let [child-keys (element-hierarchy item-key)
          map-function (fn [child-key]
                         (list-missing-selections child-key state))]
      (remove nil? (flatten (map map-function child-keys))))))

(defn get-selections
  "Get user selections and skip service information if necesasry."
  [{:keys [user-selections] :as state} keep-service-info?]
  (when user-selections
    {:user-selections
       (apply dissoc user-selections
              (if keep-service-info?
                ;; Check which selections are still valid
                (list-missing-selections :services state)
                ;; Clear all selections
                (get-child-elements :services)))}))

(defn update-org-space
  "Update a user's org/space information."
  [org-name org-guid space-name space-guid {:keys [org-space] :as state}]
  (let [cf-auth (cf/generate-auth state)
        unchanged? (and (= (org-space :org) org-name)
                        (= (org-space :space) space-name))

        services (do (println (get-command-msg
                                :login-messages
                                :loading-services))
                     (cf/get-services cf-auth space-guid))
        org-space {:org org-name
                   :space space-name
                   :guid
                     {:org org-guid
                      :space space-guid}}
        selections (get-selections state unchanged?)

        updates {:services services
                 :org-space org-space}]
    (persist/write-state (merge state updates selections))))

(defn update-user-selection
  "Update a user selection."
  [state item-key value]
  (let [{:keys [user-selections]} state
        clear-items (if (= (item-key user-selections) value)
                      []
                      (get-child-elements item-key))]
    (persist/write-state
      (assoc state :user-selections
             (merge (apply dissoc user-selections clear-items)
                    {item-key value})))))

(defn delete-user-selection
  "Remove a user selection.
  If a `predicate` function is given, the existing user selection will
  only be removed when predicate returns true for the existing selection."
  ([state item-key] (delete-user-selection state item-key identity))
  ([{:keys [user-selections] :as state} item-key predicate]
   (when (predicate (item-key user-selections))
     (let [clear-items (merge (get-child-elements item-key)
                              item-key)]
       (persist/write-state
         (assoc state :user-selections
                (apply dissoc user-selections clear-items)))))))
