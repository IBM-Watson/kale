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

(doseq [[out-path schema stopwords]
        [["resources/arabic.zip"    "solr/knowledge-expansion-ar.xml" "solr/lang/stopwords_ar.txt"]
         ["resources/brazilian.zip" "solr/knowledge-expansion-br.xml" "solr/lang/stopwords_br.txt"]
         ["resources/german.zip"    "solr/knowledge-expansion-de.xml" "solr/lang/stopwords_de.txt"]
         ["resources/english.zip"   "solr/knowledge-expansion-en.xml" "solr/lang/stopwords_en.txt"]
         ["resources/spanish.zip"   "solr/knowledge-expansion-es.xml" "solr/lang/stopwords_es.txt"]
         ["resources/french.zip"    "solr/knowledge-expansion-fr.xml" "solr/lang/stopwords_fr.txt"]
         ["resources/italian.zip"   "solr/knowledge-expansion-it.xml" "solr/lang/stopwords_it.txt"]
         ["resources/japanese.zip"  "solr/knowledge-expansion-jp.xml" "solr/lang/stopwords_jp.txt"]]]
  (zipfile out-path [["schema.xml" schema]
                     ["solrconfig.xml" "solr/solrconfig.xml"]
                     ["lang/stopwords.txt" stopwords]]))
