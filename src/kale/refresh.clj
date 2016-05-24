;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.refresh
  (:require [kale.common :refer [get-options get-command-msg
                                 reject-extra-args]]
            [kale.update :refer [update-org-space]]))

(defn get-msg
  "Return the corresponding refresh message"
   [msg-key & args]
   (apply get-command-msg :refresh-messages msg-key args))

(defn refresh
  "The refresh command."
  [{:keys [org-space] :as state} [cmd what & args] flags]
  (reject-extra-args args cmd what)
  (get-options flags {})
  (update-org-space (:org org-space)
                    (-> org-space :guid :org)
                    (:space org-space)
                    (-> org-space :guid :space)
                    state)
  (get-msg :reloaded-services (:space org-space)))
