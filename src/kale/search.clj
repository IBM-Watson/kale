;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.search
  (:require [kale.retrieve-and-rank :as rnr]
            [kale.getter :as my]
            [kale.common :refer [fail get-options new-line get-command-msg]]
            [clojure.string :as str]))

(defn get-msg
  "Return the corresponding search message"
   [msg-key & args]
   (apply get-command-msg :search-messages msg-key args))

(defn result
  [snippets {:keys [id title]}]
  (str (get-msg :id-label) id new-line
       (get-msg :title-label) title new-line
       (get-msg :snippet-label) (first (:body ((keyword id) snippets)))
       new-line))

(defn search
  "The search command."
  [{:keys [services] :as state} [cmd & terms] flags]
  (get-options flags {})
  (let [{:keys [service-key cluster-id collection-name]} (my/collection state)
        query-string (if (empty? terms)
                       "*:*"
                       (str/join "+" terms))]
    (when-not service-key
      (fail (get-msg :missing-collection)))
    (let [{:keys [response highlighting]}
          (rnr/query (:credentials (services (keyword service-key)))
                     cluster-id
                     collection-name
                     query-string)]
      (str (get-msg :found-num-results (:numFound response))
           new-line new-line
           (str/join new-line (map #(result highlighting %)
                                   (:docs response)))))))
