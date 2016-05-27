;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(require '[clojure.java.io :as io])

(import '[java.io FileOutputStream]
        '[java.util.zip ZipOutputStream ZipEntry])

(defn zipfile
  [out-path entries]
  (with-open [out (-> out-path (FileOutputStream.) (ZipOutputStream.))]
    (doseq [[name path] entries]
      (.putNextEntry out (ZipEntry. name))
      (io/copy (io/file path) out)
      (.closeEntry out))))

(io/make-parents "resources/.dummy")

(doseq [[out-path schema]
        [["resources/arabic.zip"    "solr/knowledge-expansion-ar.xml"]
         ["resources/brazilian.zip" "solr/knowledge-expansion-br.xml"]
         ["resources/german.zip"    "solr/knowledge-expansion-de.xml"]
         ["resources/english.zip"   "solr/knowledge-expansion-en.xml"]
         ["resources/spanish.zip"   "solr/knowledge-expansion-es.xml"]
         ["resources/french.zip"    "solr/knowledge-expansion-fr.xml"]
         ["resources/italian.zip"   "solr/knowledge-expansion-it.xml"]
         ["resources/japanese.zip"  "solr/knowledge-expansion-jp.xml"]]]
  (zipfile out-path [["schema.xml" schema]
                     ["solrconfig.xml" "solr/solrconfig.xml"]]))
