;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.aliases
  (:require [clojure.set :refer [union]]))

;; Define sets of aliases for various keywords we'll be looking for in
;; our command lines.

(def select
  #{"select" "sel"})

(def convert
  #{"convert" "conv"})

(def create
  #{"create" "cr"})

(def delete
  #{"delete" "del"})

(def get-command
  #{"get" "g"})

(def help
  #{"h" "help"})

(def list-info
  #{"list" "l"})

(def login
  #{"login" "logon"})

(def logout
  #{"logout" "logoff"})

(def refresh
  #{"refresh" "ref" "r"})

(def search
  #{"search" "se"})

(defn- aliases-to-keyword
  "Returns a hashmap of each alias for a keyword,
   mapped to that keyword."
  [kw]
  (reduce #(conj %1 {%2 kw})
          {}
          (eval (symbol (name kw)))))

(def commands
  (apply conj (map aliases-to-keyword [:select
                                       :convert
                                       :create
                                       :delete
                                       :get-command
                                       :help
                                       :list-info
                                       :login
                                       :logout
                                       :refresh
                                       :search])))

(def service
  #{"service" "serv"})

(def services
  (clojure.set/union #{"services"} service))

(def selections
  (clojure.set/union #{"selections" "selected"} select))

;; The next two are both used for "select":
;; need to avoid collisions or even potential confusions.
(def document-conversion
  #{"document-conversion" "document_conversion" "doc" "dc" "d"})

(def conversion-configuration
  #{"conversion-configuration" "conversion-config"
    "converter-configuration" "converter-config"
    "convert-config"})

(def retrieve-and-rank
  #{"retrieve-and-rank" "retrieve_and_rank" "retrieve" "ret" "rnr" "r"})

(def guid-option
  #{"--guid" "-guid"})

(def credentials-option
  #{"--credentials" "--cred" "-credentials" "-cred"})

(def cluster
  #{"clu" "clus" "clust" "cluster"})

(def solr-configuration
  #{"solr-configuration" "solr-config" "configuration" "config" "conf"})

(def crawler-configuration
  #{"crawler-configuration"
    "crawler-config"
    "crawl-configuration"
    "crawl-config"
    "cc"})

(def conversion
  #{"conv"
    "conver"
    "conversion"
    "convert"
    "converter"})

(def space
  #{"space" "sp"})

(def spaces
  (clojure.set/union #{"spaces"} space))

(def collection
  #{"coll" "collect" "collection"})

(def turtle
  #{"turtle"})

(def organization
  #{"organization" "org" "o"})

(def organizations
  (clojure.set/union #{"organizations" "orgs"} organization))

(def enterprise-option
  #{"--enterprise" "-enterprise" "-etp"})

(def yes-option
  #{"--yes" "-yes" "--y" "-y"})

(def trace-option
  #{"--trace" "-trace" "--t" "-t"})

(def help-option
  #{"--h" "--help" "-h" "-help"})
