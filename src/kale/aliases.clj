;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.aliases
  (:require [clojure.set :refer [union]]))

;; Define sets of aliases for various keywords we'll be looking for in
;; our command lines.

(def assemble
  #{"assemble" "a"})

(def dry-run
  #{"dry_run" "dry-run" "dryrun" "dr"})

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

(def select
  #{"select" "sel"})

(defn- aliases-to-keyword
  "Returns a hashmap of each alias for a keyword,
   mapped to that keyword."
  [kw]
  (reduce #(conj %1 {%2 kw})
          {}
          (eval (symbol (name kw)))))

(def commands
  (apply conj (map aliases-to-keyword [:assemble
                                       :dry-run
                                       :create
                                       :delete
                                       :get-command
                                       :help
                                       :list-info
                                       :login
                                       :logout
                                       :refresh
                                       :search
                                       :select])))

(def service
  #{"service" "serv"})

(def services
  (clojure.set/union #{"services"} service))

(def selections
  (clojure.set/union #{"selections" "selected"} select))

;; The next two are both used for "select":
;; need to avoid collisions or even potential confusions.
(def document-conversion
  #{"document-conversion" "document_conversion"
    "doc" "dc" "d"})

(def conversion-configuration
  #{"conversion-configuration" "conversion_configuration"
    "conversion-config" "conversion_config"
    "converter-configuration" "converter_configuration"
    "converter-config" "converter_config"
    "convert-config" "convert_config"})

(def retrieve-and-rank
  #{"retrieve-and-rank" "retrieve_and_rank"
    "retrieve" "ret" "rnr" "r"})

(def guid-option
  #{"--guid" "-guid"})

(def credentials-option
  #{"--credentials" "--cred" "-credentials" "-cred"})

(def cluster
  #{"clu" "clus" "clust" "cluster"})

(def solr-configuration
  #{"solr-configuration" "solr_configuration"
    "solr-config" "solr_config"
    "configuration" "config" "conf"})

(def crawler-configuration
  #{"crawler-configuration" "crawler_configuration"
    "crawler-config" "crawler_config"
    "crawl-configuration" "crawl_configuration"
    "crawl-config" "crawl_config"
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

(def organization
  #{"organization" "org" "o"})

(def organizations
  (clojure.set/union #{"organizations" "orgs"} organization))

(def sso-option
  #{"--sso" "-sso" "-s"})

(def premium-option
  #{"--premium" "-premium" "-prem"})

(def wait-option
  #{"--wait" "-wait" "-w"})

(def retry-option
  #{"--retry" "-retry" "-r"})

(def yes-option
  #{"--yes" "-yes" "--y" "-y"})

(def trace-option
  #{"--trace" "-trace" "--t" "-t"})

(def help-option
  #{"--h" "--help" "-h" "-help"})
