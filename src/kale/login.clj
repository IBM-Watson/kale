;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.login
  (:require [kale.persistence :refer [write-state]]
            [kale.cloud-foundry :as cf]
            [kale.list :refer [list-working-environment]]
            [kale.update :refer [get-selections]]
            [kale.common :refer [fail my-language new-line
                                prompt-user prompt-user-hidden
                                get-options reject-extra-args
                                get-command-msg]]
            [clojure.string :as str]))

(defn get-msg
  "Return the corresponding login/logout message"
   [msg-key & args]
   (apply get-command-msg :login-messages msg-key args))

(defn get-username
  "Determine the username for login"
  [state username-arg]
  (if (some? username-arg)
    (do (println (get-msg :using-username username-arg))
        username-arg)
    (let [default-username (-> state :login :username)
          prompt (if (some? default-username)
                   (get-msg :prompt-username-default default-username)
                   (get-msg :prompt-username))
          allow-blank? (some? default-username)
          username (prompt-user prompt allow-blank?)]
      (if-not (str/blank? username)
        username
        (do (println (get-msg :using-username default-username))
            default-username)))))

(defn check-endpoint
  "Check if the endpoint provided is valid, which is determined
  by whether or not it matches a specific pattern"
  [endpoint]
  (when (nil? (re-matches #"https://api.*bluemix.net" endpoint))
    (println (get-msg :invalid-endpoint endpoint))))

(defn get-endpoint
  "Determine the endpoint for login"
  [state endpoint-arg]
  (if (some? endpoint-arg)
    (do (check-endpoint endpoint-arg)
        (println (get-msg :using-endpoint endpoint-arg))
        endpoint-arg)
    (let [default-endpoint (or (-> state :login :endpoint)
                               "https://api.ng.bluemix.net")
          endpoint (prompt-user
                     (get-msg :prompt-endpoint-default default-endpoint)
                     true)]
      (if-not (str/blank? endpoint)
        endpoint
        (do (println (get-msg :using-endpoint default-endpoint))
            default-endpoint)))))

(defn get-env
  "Get environment variable"
  [varname]
  (System/getenv varname))

(defn get-password
  "Determine the password for login"
  []
  (if-let [password (get-env "KALE_PASSWORD")]
    (do (println (get-msg :using-password))
        password)
    (prompt-user-hidden (get-msg :prompt-password) false)))

(defn attempt-to-get-org
  "Attempt to retrieve the specified org, and use the local org or first
  listed org if the specified one doesn't exist"
  [cf-auth org-name username]
  (let [orgs (cf/get-organizations cf-auth)
        attempt (cf/find-entity orgs org-name)
        ;; This assumes the user hasn't changed the name of their local org
        default (or (cf/find-entity orgs username)
                    (first orgs))]
    (or attempt
        (do (if org-name
              (println (get-msg :alternative-org
                                org-name
                                (-> default :entity :name)))
              (println (get-msg :using-org (-> default :entity :name))))
            default))))

(defn attempt-to-get-space
  "Attempt to retrieve the specified space, and use the first listed space
  if the specified one doesn't exist"
  [cf-auth org-guid space-name]
  (let [spaces (cf/get-spaces cf-auth org-guid)
        attempt (cf/find-entity spaces space-name)
        default (first spaces)]
    (or attempt
        (do (if space-name
              (println (get-msg :alternative-space
                                space-name
                                (-> default :entity :name)))
              (println (get-msg :using-space (-> default :entity :name))))
            default))))

(defn get-org-space
  "Loads information related the user's current org and space"
  [cf-auth org-name space-name username]
  (let [org (attempt-to-get-org cf-auth org-name username)
        org-guid (-> org :metadata :guid)
        space (attempt-to-get-space cf-auth org-guid space-name)]
    {:org (-> org :entity :name)
     :space (-> space :entity :name)
     :guid {:org org-guid
            :space (-> space :metadata :guid)}}))

(defn load-user-info
  "Log into Cloud Foundry and get user information"
  [username password endpoint state]
  (let [{:keys [access_token]} (cf/get-oauth-tokens username password endpoint)
        cf-auth {:url endpoint :token access_token}
        {:keys [org space]} (state :org-space)
        org-space (get-org-space cf-auth org space username)
        space-guid (-> org-space :guid :space)
        services (do (println (get-msg :loading-services))
                     (cf/get-services cf-auth space-guid))]
    {:login {:username username
             :cf-token access_token
             :endpoint endpoint}
     :services services
     :org-space org-space}))

(defn login
  "Allow the user to login.  Pulls in some access credentials
   and other information from Bluemix, which runs on Cloud Foundry."
  [state [cmd username-arg endpoint-arg password-arg & args] flags]
  (reject-extra-args args cmd)
  (get-options flags {})
  (let [username (get-username state username-arg)
        endpoint (get-endpoint state endpoint-arg)
        password (get-password)
        prev-endpoint? (= endpoint (-> state :login :endpoint))
        prev-username? (= username (-> state :login :username))

        user-info (do (println (get-msg :login-start))
                      (load-user-info
                        username password endpoint
                        (merge state
                               (when-not prev-username? {:org-space {}}))))
        selections (get-selections state (and prev-endpoint? prev-username?))
        new-state (merge state user-info selections)]
      (write-state new-state)
      (str (get-msg :login-done) new-line new-line
           (list-working-environment new-state))))

(defn logout
  "Log the user out, by removing their login information from
  our persistent state."
  [state [cmd & args] flags]
  (reject-extra-args args cmd)
  (get-options flags {})
  (println (get-msg :logout-start))
  (write-state
  (dissoc (update-in (update-in state
                                [:org-space] dissoc :guid)
                     [:login] dissoc :cf-token)
          :services))
  (get-msg :logout-done))
