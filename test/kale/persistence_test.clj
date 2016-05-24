;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.persistence-test
  (:require [kale.persistence :as sut]
            [kale.common :refer [new-line]]
            [clojure.test :as t]
            [clojure.java.io :as io]))

(def test-state-filename "state-file-for-test.json")

(t/deftest read-happy-path
  (spit test-state-filename "{\"read\":[\"happy\",\"path\"]}")
  (try
    (t/is (= {:read ["happy" "path"]} (sut/read-state test-state-filename)))
    (finally (io/delete-file test-state-filename))))

(t/deftest read-no-state-file
  (spit test-state-filename "")
  (io/delete-file test-state-filename)  ; fails test if file cannot be deleted
  (t/is (= {} (sut/read-state test-state-filename))))

(t/deftest read-invalid-json
  (spit test-state-filename "{\"invalid\":\"json\" ")
  (try
    (t/is (thrown-with-msg? com.fasterxml.jackson.core.JsonParseException
                            #"Unexpected end-of-input"
                            (sut/read-state test-state-filename)))
    (finally (io/delete-file test-state-filename))))

(t/deftest write-happy-path
  (try
    (t/is (= (str "{" new-line
                  "  \"write\" : [ \"happy\", \"path\" ]" new-line
                  "}")
             (do (sut/write-state {:write ["happy" "path"]} test-state-filename)
                 (slurp test-state-filename))))
    (finally (io/delete-file test-state-filename))))
