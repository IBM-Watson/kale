;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.assemble-test
  (:require [kale.assemble :as sut]
            [clj-time.core :refer [in-minutes]]
            [kale.common :refer [new-line] :as common]
            [clojure.test :refer [deftest is]]
            [slingshot.test :refer :all]
            [kale.retrieve-and-rank :as rnr]))

(common/set-language :en)

(defn test-command
  [_ args flags]
  (format "Running %s with options %s" args flags))

(defn bad-command
  [_ _ _]
  (common/fail "Something bad happened."))

(deftest run-assemble-success
  (is (= (str "[Running command 'kale foo bar']" new-line
              "Running [\"foo\" \"bar\"] with options []" new-line
              "[Running command 'kale bar foo -a']" new-line
              "Running [\"bar\" \"foo\"] with options [\"-a\"]" new-line)
         (with-out-str (sut/run-wizard [[test-command ["foo" "bar"] []]
                                        [test-command ["bar" "foo"] ["-a"]]]
                                       (fn [] "rollback"))))))

(deftest run-wizard-rollback
  (is (= (str "[Running command 'kale foo bar']" new-line
              "Running [\"foo\" \"bar\"] with options []" new-line
              "[Running command 'kale bar foo -a']" new-line
              "Something bad happened." new-line
              "rollback" new-line)
         (with-out-str (sut/run-wizard [[test-command ["foo" "bar"] []]
                                        [bad-command ["bar" "foo"] ["-a"]]]
                                       (fn [] "rollback"))))))

(deftest assemble-missing-name
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"Please specify the base name to use for the components."
       (sut/assemble {} ["assemble"] []))))

(deftest assemble-missing-config-name
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"Please specify the name of the Solr configuration to create."
       (sut/assemble {} ["assemble" "turtle"] []))))

(deftest assemble-no-zip-file-specified
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"'my-conf' is not a prepackaged Solr configuration."
       (sut/assemble {} ["assemble" "turtle" "my-conf"] []))))

(deftest assemble-missing-zip-file
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"Cannot read the file named 'no-such-config.zip'."
       (sut/assemble {} ["assemble" "turtle"
                         "my-conf" "no-such-config.zip"] []))))

(deftest assemble-prepackaged-config
  (with-redefs [sut/wizard-command (fn [_ _ _ _ _])
                sut/run-wizard (fn [_ _])]
    (is (= (str "Enhanced Information Retrieval instance 'turtle'"
                " creation successful!")
           (sut/assemble {} ["assemble" "turtle" "english"] [])))))

(deftest assemble-config-from-file
  (with-redefs [common/readable-files? (fn [_] true)
                rnr/upload-config (fn [_ _ _ _] nil)
                sut/wizard-command (fn [_ _ _ _ _])
                sut/run-wizard (fn [_ _])]
    (is (= (str "Enhanced Information Retrieval instance 'turtle'"
                " creation successful!")
           (sut/assemble {} ["assemble" "turtle"
                             "my-conf" "test-config.zip"] [])))))
