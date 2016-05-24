;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.main
  (:require [kale.aliases :refer [commands] :as aliases]
            [kale.getter :refer [user-selection]]
            [kale.common :refer [fail set-trace set-language
                                 get-command-msg]]
            [kale.select]
            [kale.convert]
            [kale.create]
            [kale.delete]
            [kale.persistence]
            [kale.get-command]
            [kale.help]
            [kale.list]
            [kale.login]
            [kale.refresh]
            [kale.search]
            [slingshot.slingshot :refer [try+]])
  (:gen-class))

(defn get-msg
  "Return the corresponding main message"
   [msg-key & args]
   (apply get-command-msg :main-messages msg-key args))

(defn isFlag? [x] (-> (re-find #"^-" x) nil? not))

(def global-options {:trace aliases/trace-option
                     :help aliases/help-option})

(defn extract-global-options
  "Pull out global options from CLI flags"
  [flags]
  (if (empty? flags)
    {:options {}
     :cmd-flags []}
    (let [flag (first flags)
          match-flag (fn [info] (contains? (second info) flag))
          match (first (filter match-flag global-options))
          {:keys [options cmd-flags]} (extract-global-options (next flags))]
      {:options (merge (when match {(first match) true}) options)
       :cmd-flags (if (nil? match)
                    (merge cmd-flags flag)
                    cmd-flags)})))

(defn read-flags
  "Parse CLI arguments and pull out any flags and global options"
  [arguments]
  (let [args (remove isFlag? arguments)
        flags (filter isFlag? arguments)
        {:keys [options cmd-flags]} (extract-global-options flags)]
  {:args args
   :flags cmd-flags
   :options options}))

(defn not-yet-implemented
  [state args]
  (fail (get-msg :not-implemented (first args))))

(def verbs
  "Our supported verbs and the functions implementing each"
  {:convert        kale.convert/convert
   :create         kale.create/create
   :delete         kale.delete/delete
   :get-command    kale.get-command/get-command
   :help           kale.help/help
   :list-info      kale.list/list-info
   :login          kale.login/login
   :logout         kale.login/logout
   :refresh        kale.refresh/refresh
   :search         kale.search/search
   :select         kale.select/select})

(defn get-cmd-name
  "Determine the command name to output"
  [cmd-key]
  (if-let [cmd-name ({:list-info "list"
                      :get-command "get"}
                      cmd-key)]
    cmd-name
    (name cmd-key)))

(defn error-exit
  "Exit with an error code.
  This function is separated out to allow tests to redefine it."
  ([] (error-exit 1))
  ([exit-status] (System/exit exit-status)))

(def ^:const byte-array-class (class (byte-array 0)))

(defn handle-fail
  "kale.common/fail exception handling"
  [{:keys [message]}]
  (println message)
  (error-exit))

(defn handle-http
  "HTTP exception handling"
  [{:keys [status body trace-redirects]}]
  (println (get-msg :http-call (first trace-redirects)))
    (if (neg? status)
      (println (get-msg :http-exception))
      (println (get-msg :http-error-status status)))
    (if (= byte-array-class (class body))
      (println (String. body))
      (println body))
    (error-exit))

(defn handle-other
  "Generic exception handling"
  [e]
  (println (get-msg :other-exception))
  (println (str e))
  (error-exit))

(defn -main
  "The command line entry point."
  [& arguments]
  (let [{:keys [args flags options]} (read-flags arguments)
        command (commands (first args))
        state (kale.persistence/read-state)
        language (keyword (user-selection state :language))]
    (try+
      (when (some? (options :trace))
        (set-trace true))
      (when (some? language)
        (set-language language))

      (if (or (some? (options :help))
                  (some? (aliases/help (second args))))
        (println (kale.help/help {} (concat ["help"] args) []))
        (if (nil? command)
          (println (kale.help/help {} (concat ["help"] args) flags))
          (do (when-not (or (#{:help :login} command) (:services state))
                (println (get-msg :please-login (get-cmd-name command)))
                (error-exit))
              (println ((verbs command) state args flags)))))

      (catch [:type :kale.common/fail] e (handle-fail e))
      (catch (number? (:status %)) e (handle-http e))
      (catch Exception e (handle-other e)))))
