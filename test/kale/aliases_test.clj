;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.aliases-test
  (:require [kale.aliases :as sut]
            [clojure.test :refer [deftest is]]))

(def alias-count-for-commands
  (apply + (map count [sut/select
                       sut/convert
                       sut/create
                       sut/delete
                       sut/get-command
                       sut/help
                       sut/list-info
                       sut/login
                       sut/logout
                       sut/refresh
                       sut/search])))

(deftest alias-collisions
  (is (= (count sut/commands) alias-count-for-commands)
      (str "The count of the command map, "
           (count sut/commands)
           ", does not match the sum of the alias counts, "
           alias-count-for-commands
           ". This probably means there is an alias collision.")))
