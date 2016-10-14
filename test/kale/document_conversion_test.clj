;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.document-conversion-test
  (:require [kale.document-conversion :as dc]
            [kale.common :refer [set-language]]
            [org.httpkit.fake :refer [with-fake-http]]
            [clojure.test :refer :all]
            [slingshot.test :refer :all]))

(set-language :en)

(deftest rnr-request-no-credentials
  (is (thrown+-with-msg?
         [:type :kale.common/fail]
         #"Target document_conversion service has no access credentials."
         (dc/convert nil
                     "2016-03-15"
                     "{\"conversion_target\":\"NORMALIZED_TEXT\"}"
                     "Unconverted text"))))

(def template-response
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :request-time 100
   :body ""
   :cookies {"Watson-DPAT" {:discard true
                            :path "/document-conversion/api"
                            :secure true
                            :value "LONG"
                            :version 0}}})

(def endpoint
  {:url "https://gateway.watsonplatform.net/document-conversion/api",
   :username "user"
   :password "pwd"})

(defn dc-url
  [version]
  (-> endpoint :url (str "/v1/index_document?version=" version)))

(defn respond
  [partial-response]
  (merge template-response partial-response))

(deftest happy-path
  (with-fake-http
    [(dc-url "2016-03-15")
     (respond {:body "Converted document"})]
    (is (= "Converted document"
           (dc/convert endpoint
                       "2016-03-15"
                       "{\"conversion_target\":\"NORMALIZED_TEXT\"}"
                       "Unconverted text")))))
