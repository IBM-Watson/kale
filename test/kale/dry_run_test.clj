;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.dry-run-test
  (:require [clojure.test :refer [deftest is]]
            [kale.common :refer [new-line set-language]]
            [kale.document-conversion :as dc]
            [slingshot.test :refer :all]
            [clj-http.fake :refer [with-fake-routes-in-isolation]]
            [kale.dry-run :as sut]))

(set-language :en)

(def config-file "test-convert.json")
(def test-file "test-file.html")
(def missing-file "missing-file")
(def converted-text "{
  \"media_type_detected\" : \"text/html\",
  \"metadata\" : [ ],
  \"answer_units\" : [ {
    \"id\" : \"58f0bb7b-6e90-4fb1-9e0d-0a8868ab97ca\",
    \"type\" : \"body\",
    \"title\" : \"no-title\",
    \"direction\" : \"ltr\",
    \"content\" : [ {
      \"media_type\" : \"text/html\",
      \"text\" : \"Text before conversion.\"
    }, {
      \"media_type\" : \"text/plain\",
      \"text\" : \"Text before conversion.\"
    } ]
  } ],
  \"warnings\" : [ ],
  \"solr_document\" : {
    \"body\" : \"Text before conversion.\",
    \"title\" : \"no-title\"
  }
  }")

(spit test-file (str "Text before conversion." new-line))

(deftest dry-run-no-files
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"Please specify one or more file names."
       (sut/dry-run {} ["dry-run"] []))))

(deftest dry-run-missing-config-file
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       (re-pattern (str "Couldn't read conversion configuration file '"
                        missing-file "'."))
       (sut/dry-run {:user-selections
                     {:conversion-configuration-file missing-file}}
                    ["dry-run" test-file] []))))

(deftest dry-run-invalid-config-file
  (spit config-file "{ \"convert_document\" : \"unclosed quote }")
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       (re-pattern (str "'" config-file "' is not valid JSON"))
       (sut/dry-run {:user-selections
                     {:conversion-configuration-file config-file}}
                    ["dry-run" test-file] []))))

(deftest dry-run-missing-file
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       (re-pattern (str "Cannot read the file named '" missing-file "'."))
       (sut/dry-run {} ["dry-run" missing-file] []))))

;; Should have tests for service errors, such as: invalid configuration

(deftest dry-run-happy-path
  (spit config-file "{\"conversion_target\":\"NORMALIZED_TEXT\"}")
  (with-redefs [dc/convert (fn [_ _ _ _] converted-text)]
    (is (= (str "Converting 'test-file.html' ... completed." new-line)
           (with-out-str
             (is (= converted-text
                    (do (sut/dry-run
                         {:user-selections
                          {:conversion-configuration-file config-file}}
                         ["dry-run" "test-file.html"] [])
                        (slurp "converted/test-file.html.json")))))))))

(deftest dry-run-no-config
  (with-redefs [dc/convert (fn [_ _ _ _] converted-text)]
    (is (= (str "Note: Using the default conversion configuration: \"{}\"."
                new-line
                "Converting 'test-file.html' ... completed." new-line)
           (with-out-str
             (is (= converted-text
                    (do (sut/dry-run {}
                                     ["dry-run" "test-file.html"] [])
                        (slurp "converted/test-file.html.json")))))))))

(deftest dry-run-failure
  (is (= (str "Note: Using the default conversion configuration: \"{}\"."
              new-line
              "Converting 'test-file.html' ..." new-line
              "Conversion failed for 'test-file.html':"
              new-line
              "java.net.MalformedURLException:"
              " no protocol: /v1/index_document?version=2016-03-18"
              new-line)
         (with-out-str
           (is (= (str new-line "Conversion completed."
                       " Please find the converted output"
                       " in the directory 'converted'." new-line)
                  (sut/dry-run {:services {:dc {:type "document_conversion"
                                                :credentials {}}}}
                               ["dry-run" "test-file.html"]
                               [])))))))
