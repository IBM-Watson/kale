;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.help-test
  (:require [kale.help :as sut]
            [kale.common :as common]
            [clojure.test :as t]))

(common/set-language :en)

(t/deftest help-overview
  (t/is (= (sut/get-msg :help)
           (sut/help {} ["help"] []))))
