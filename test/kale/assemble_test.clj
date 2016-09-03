;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.assemble-test
  (:require [kale.assemble :as sut]
            [kale.create :refer [create]]
            [kale.select :refer [select]]
            [kale.delete :refer [delete]]
            [clj-time.core :refer [in-minutes]]
            [kale.common :refer [new-line] :as common]
            [clojure.test :refer [deftest is]]
            [slingshot.test :refer :all]
            [kale.retrieve-and-rank :as rnr]))

(common/set-language :en)

(defn test-command [_ _ _])

(defn bad-command
  [_ _ _]
  (common/fail "Something bad happened."))

(deftest run-assemble-success
  (is (= (str "[Running command 'kale foo bar']" new-line
              "[Running command 'kale bar foo -a']" new-line)
         (with-out-str (sut/run-wizard [[test-command ["foo" "bar"] []]
                                        [test-command ["bar" "foo"] ["-a"]]]
                                       (fn [] "rollback"))))))

(deftest run-wizard-rollback
  (is (= (str "[Running command 'kale foo bar']" new-line
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

(deftest assemble-prepackaged-bad-cluster-size
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"Cluster size must be an integer in the range of 1 to 99."
       (sut/assemble {} ["assemble" "turtle" "english" "100"] []))))

(deftest assemble-missing-zip-file
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"Cannot read the file named 'no-such-config.zip'."
       (sut/assemble {} ["assemble" "turtle"
                         "my-conf" "no-such-config.zip"] []))))

(deftest assemble-config-from-file-bad-cluster-size
  (with-redefs [common/readable-files? (fn [_] true)
                rnr/upload-config (fn [_ _ _ _] nil)]
    (is (thrown+-with-msg?
         [:type :kale.common/fail]
         #"Cluster size must be an integer in the range of 1 to 99."
         (sut/assemble {} ["assemble" "turtle"
                           "my-conf" "test-config.zip" "100"] [])))))

(defn assemble-output
  [create-dc-args create-config-args]
  (str "[Running command 'kale create space turtle']" new-line
       "[Running command 'kale create document_conversion " create-dc-args
       "']" new-line
       "[Running command 'kale create retrieve_and_rank turtle-rnr']" new-line
       "[Running command 'kale create cluster turtle-cluster --wait']" new-line
       "[Running command 'kale create solr-configuration " create-config-args
       "']" new-line
       "[Running command 'kale create collection turtle-collection']"
       new-line))

(deftest assemble-prepackaged-config
  (with-redefs [create test-command]
    (is (= (assemble-output "turtle-dc" "english")
           (with-out-str
             (is (= (str "Enhanced Information Retrieval instance 'turtle'"
                         " creation successful!")
                    (sut/assemble {} ["assemble" "turtle" "english"] []))))))))

(deftest assemble-config-from-file
  (with-redefs [create test-command
                common/readable-files? (fn [_] true)
                rnr/upload-config (fn [_ _ _ _] nil)]
    (is (= (assemble-output "turtle-dc" "my-conf test-config.zip")
           (with-out-str
             (is (= (str "Enhanced Information Retrieval instance 'turtle'"
                         " creation successful!")
                    (sut/assemble {} ["assemble" "turtle"
                                      "my-conf" "test-config.zip"] []))))))))

(deftest assemble-premium
  (with-redefs [create test-command]
    (is (= (str "Warning: The 'premium' plan is currently not available for"
                 new-line "retrieve_and_rank services. "
                "Using the 'standard' plan instead." new-line
                (assemble-output "turtle-dc --premium" "english"))
           (with-out-str
             (is (= (str "Enhanced Information Retrieval instance 'turtle'"
                         " creation successful!")
                         (sut/assemble {} ["assemble" "turtle" "english"]
                                          ["-prem"]))))))))
