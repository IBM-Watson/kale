;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.refresh-test
  (:require [kale.refresh :as sut]
            [kale.update :refer [update-org-space]]
            [kale.common :refer [set-language]]
            [clojure.test :as t :refer [deftest is]]))

(set-language :en)

(t/deftest refresh-services
  (with-redefs [update-org-space
                (fn [org-name org-guid space-name space-guid _]
                  (is (and (= org-name "org-name")
                           (= org-guid "ORG_GUID")
                           (= space-name "space-name")
                           (= space-guid "SPACE_GUID"))))]
      (is (= "Reloaded services in space 'space-name'."
             (sut/refresh {:org-space {:org "org-name"
                                       :space "space-name"
                                       :guid {:org "ORG_GUID",
                                              :space "SPACE_GUID"}}}
                          ["refresh"]
                          [])))))
