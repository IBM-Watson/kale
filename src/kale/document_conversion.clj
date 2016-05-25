;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.document-conversion
  (:require [kale.watson-service :as ws]
            [kale.common :refer [fail new-line get-command-msg]]
            [slingshot.slingshot :refer [throw+]]))

(defn dc-request
  "Make a HTTP request, but first check for missing credentials."
  [& args]
  (if (nil? (second args))
    (fail (get-command-msg :service-messages :dc-no-creds))
    (apply ws/text args)))

(defn convert
  "Convert a single document through the Watson Document Conversion Service.
   Returns the body of the response as a string."
  [endpoint version config file]
  (dc-request :post endpoint (str "/v1/index_document?version="
                                  version)
              (ws/setup-multipart {:config config
                                   :file file})))
