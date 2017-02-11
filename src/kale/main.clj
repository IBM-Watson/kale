;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.main
  (:require [kale.aliases :refer [commands] :as aliases]
            [kale.getter :refer [user-selection]]
            [kale.common :refer [fail try-function get-command-msg
                                 set-trace set-language]]
            [kale.assemble]
            [kale.dry-run]
            [kale.create]
            [kale.delete]
            [kale.persistence]
            [kale.get-command]
            [kale.help]
            [kale.list]
            [kale.login]
            [kale.refresh]
            [kale.search]
            [kale.select])
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
  {:assemble       kale.assemble/assemble
   :dry-run        kale.dry-run/dry-run
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

(defn main
  [& arguments]
  (let [{:keys [args flags options]} (read-flags arguments)
        command (commands (first args))
        state (kale.persistence/read-state)
        language (keyword (user-selection state :language))]
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
              (fail (get-msg :please-login (get-cmd-name command))))
            (println ((verbs command) state args flags)))))))

(defn -main
  "The command line entry point."
  [& arguments]
  (try-function main arguments error-exit))
