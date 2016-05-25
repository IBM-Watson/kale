;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.help
  (:require [kale.aliases :refer [commands]]
            [kale.common :refer [my-language get-options
                                 get-command-msg]]))

(defn get-msg
  "Return the corresponding help message"
   [msg-key & args]
   (apply get-command-msg :help-messages msg-key args))

(defn help
  "The help action. Defaults to English."
  [state [cmd what-str & args] flags]
  (get-options flags {})
  (if-let [what (if (some? what-str)
                  (commands what-str)
                  :help)]
    (get-msg what)
    (get-msg :no-help-msg what-str)))
