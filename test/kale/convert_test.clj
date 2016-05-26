;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.convert-test
  (:require [clojure.test :refer [deftest is]]
            [kale.common :refer [new-line set-language]]
            [kale.document-conversion :as dc]
            [slingshot.test :refer :all]
            [clj-http.fake :refer [with-fake-routes-in-isolation]]
            [kale.convert :as sut]))

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

(deftest convert-no-files
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"Please specify one or more file names."
       (sut/convert {} ["conv"] []))))

(deftest convert-missing-config-file
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       (re-pattern (str "Couldn't read conversion configuration file '"
                        missing-file "'."))
       (sut/convert {:user-selections
                     {:conversion-configuration-file missing-file}}
                    ["conv" test-file] []))))

(deftest convert-invalid-config-file
  (spit config-file "{ \"convert_document\" : \"unclosed quote }")
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       (re-pattern (str "'" config-file "' is not valid JSON"))
       (sut/convert {:user-selections
                     {:conversion-configuration-file config-file}}
                    ["conv" test-file] []))))

(deftest convert-missing-file
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       (re-pattern (str "Cannot read the file named '" missing-file "'."))
       (sut/convert {} ["conv" missing-file] []))))

;; Should have tests for service errors, such as: invalid configuration

(deftest convert-happy-path
  (spit config-file "{\"conversion_target\":\"NORMALIZED_TEXT\"}")
  (with-redefs [dc/convert (fn [_ _ _ _] converted-text)]
    (is (= (str "Converting 'test-file.html' ... completed." new-line)
           (with-out-str
             (is (= converted-text
                    (do (sut/convert
                         {:user-selections
                          {:conversion-configuration-file config-file}}
                         ["conv" "test-file.html"] [])
                        (slurp "converted/test-file.html.json")))))))))

(deftest convert-no-config
  (with-redefs [dc/convert (fn [_ _ _ _] converted-text)]
    (is (= (str "Warning: Using an empty configuration: \"{}\"." new-line
            "Converting 'test-file.html' ... completed." new-line)
           (with-out-str
             (is (= converted-text
                    (do (sut/convert {}
                         ["conv" "test-file.html"] [])
                        (slurp "converted/test-file.html.json")))))))))

(deftest convert-failure
  (is (= (str "Warning: Using an empty configuration: \"{}\"." new-line
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
                  (sut/convert {:services {:dc {:type "document_conversion"
                                                :credentials {}}}}
                               ["conv" "test-file.html"]
                               [])))))))
