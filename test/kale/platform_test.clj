(ns kale.platform-test
  (:require [kale.platform :as sut]
            [slingshot.test :refer :all]
            [clojure.test :refer [deftest is]]))

(deftest parse-int-empty
  (is (thrown+? NumberFormatException
                (sut/parse-int ""))))

(deftest parse-int-non-numeric
  (is (thrown+? NumberFormatException
                (sut/parse-int "nope-no-digits-here"))))

(deftest parse-int-numeric
  (is (= 123456789012345
         (sut/parse-int "123456789012345"))))
