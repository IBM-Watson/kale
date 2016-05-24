;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.common
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [slingshot.slingshot :refer [throw+]]
            [kale.messages.en :as en]
            [kale.messages.es :as es]
            [kale.messages.fr :as fr]))

(defn fail
  "Output message and exit"
  [msg]
  (throw+ {:type ::fail :message msg}))

(def languages
  {:en en/messages
   :es es/messages
   :fr fr/messages})

;; Assume that the user's environment is setup for their preferred
;; language.
(def my-language
  "The two letter language code for this user's language."
  (atom
    (let [locale-key (keyword (.getLanguage (java.util.Locale/getDefault)))]
      (if (contains? languages locale-key)
        locale-key
        :en))))

(defn get-command-msg
  "Return the corresponding command message string based on the
   user's langauge"
  [command-key msg-key & args]
  (let [messages (@my-language languages)]
    (if-let [msg (-> messages command-key msg-key)]
      (apply format msg args)
      (str "[unknown " @my-language " message " command-key " " msg-key "]"))))

(defn get-msg
  "Return the corresponding common message"
   [msg-key & args]
   (apply get-command-msg :common-messages msg-key args))

(defn set-language
  "Set the language key to be used when looking up messages"
  [lang-key]
  (if (contains? languages lang-key)
    (reset! my-language lang-key)
    (fail (get-msg :unknown-language (name lang-key)))))

(def ^:const new-line (System/getProperty "line.separator"))

;; The value for cli-options should be in the following format:
;; {:opt1 #{"-opt1" "--opt1"}
;;  :opt2 #{"-opt2" "--opt2"}}

(defn get-options
  "Check if each flag matches any known options and returns an
  option entry for that flag"
  [flags cli-options]
  (let [map-function (fn [flag]
            (let [match-flag (fn [info] (contains? (second info) flag))
                  match (first (filter match-flag cli-options))]
              (if (nil? match)
                (fail (get-msg :unknown-option flag))
                {(first match) true})))]
    (into {} (map map-function flags))))

(defn reject-extra-args
  "Throws error when there extra arguments provided to the command"
  [args & cmd]
  (when (seq args)
    (fail (get-msg :too-many-args (str/join " " cmd) (str/join " " args)))))

(defn prompt-user
  "Prompts the user using the provided string"
  [prompt allow-blank?]
  (print prompt)
  (flush)
  (let [input (read-line)]
    (if (and (not allow-blank?) (str/blank? input))
       (fail (get-msg :invalid-input))
       input)))

(defn prompt-user-hidden
  "Prompts the user without echoing the characters"
  [prompt allow-blank?]
  (if-let [console (System/console)]
    (let [input (clojure.string/join (.readPassword console prompt nil))]
      (if (and (not allow-blank?) (str/blank? input))
        (fail (get-msg :invalid-input))
        input))
    ;; lein doesn't know to handle (System/console) for some reason,
    ;; so we'll default to prompt-user if getting the console fails
    (prompt-user prompt allow-blank?)))

(defn prompt-user-yn
  "Prompts the user, expecting a yes-no answer"
  [prompt]
  (let [response (.toLowerCase (prompt-user (str prompt " (y/n)? ") false))]
    (cond
      (or (= response "yes") (= response "y")) true
      (or (= response "no") (= response "n")) false
      :else (fail (get-msg :invalid-input)))))

(defn check-files
  "Checks a sequence of filenames to see if each matches a condition.
  If no filenames are given or any of the named files fail, fail
  with an error message. Always returns 'true'."
  [filenames operation op-str]
  (let [unreadable (remove operation filenames)]
    (when (empty? filenames)
      (fail (get-msg :missing-filenames)))
    (when (= 1 (count unreadable))
      (fail (get-msg :unreadable-file op-str (first unreadable))))
    (when (< 1 (count unreadable))
      (fail (get-msg :unreadable-files op-str (str/join ", " unreadable))))
    true))

(defn readable-files?
  [filenames]
  (check-files filenames
               (fn [file] (.canRead (io/file file)))
               (get-msg :read)))

(defn writable-files?
  [filenames]
  (check-files filenames
               (fn [file] (.canWrite (io/file file)))
                (get-msg :write-to)))

(def trace-enabled (atom false))

(defn set-trace
  "Enable or disable tracing for API calls"
  [value]
  (reset! trace-enabled value))

(defn unknown-action
  "Fail the command because the action isn't recognized."
  [what command actions]
  (fail (str
      (if (empty? what)
        (get-msg :missing-action command)
        (get-msg :unknown-action command what))
      new-line (get-msg :available-actions command)
      new-line "   " (str/join (str new-line "   ") actions))))
