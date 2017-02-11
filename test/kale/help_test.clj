;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.help-test
  (:require [kale.help :as sut]
            [kale.common :as common]
            [slingshot.test :refer :all]
            [clojure.test :as t]))

(common/set-language :en)

(t/deftest help-overview
  (t/is (= (sut/get-msg :help)
           (sut/help {} ["help"] []))))

(t/deftest help-unknown
  (t/is (thrown+-with-msg?
         [:type :kale.common/fail]
         #"I'm sorry. I don't have any help for 'action'"
         (sut/help {} ["help" "action"] []))))
